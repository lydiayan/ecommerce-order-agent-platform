package com.css.mallorderagent.skill.source;

import com.css.mallorderagent.config.OrderAgentProperties;
import com.css.mallorderagent.skill.model.DownloadedSkillPackage;
import com.css.mallorderagent.skill.model.SkillSourceRequest;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class GitArchiveSkillSourceClient implements SkillSourceClient {

    private final SkillSourcePolicy sourcePolicy;
    private final OrderAgentProperties.SkillProperties properties;
    private final HttpClient httpClient;

    public GitArchiveSkillSourceClient(SkillSourcePolicy sourcePolicy, OrderAgentProperties properties) {
        this.sourcePolicy = sourcePolicy;
        this.properties = properties.getSkill();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(this.properties.getConnectTimeoutMillis()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public DownloadedSkillPackage download(SkillSourceRequest request) {
        if (request == null || request.sourceUrl() == null || request.sourceUrl().isBlank()) {
            throw new IllegalArgumentException("sourceUrl must not be blank");
        }
        String ref = validateRef(request.normalizedRef());
        String sourcePath = normalizeRelativePath(request.normalizedPath());
        URI sourceUri = sourcePolicy.validate(URI.create(request.sourceUrl().trim()));
        URI archiveUri = sourcePolicy.validate(toArchiveUri(sourceUri, ref));
        byte[] archive = downloadArchive(archiveUri, 0);
        Map<String, byte[]> files = selectSkillFiles(extractArchive(archive), sourcePath);
        long totalBytes = files.values().stream().mapToLong(bytes -> bytes.length).sum();
        return new DownloadedSkillPackage(Map.copyOf(files), digest(files), totalBytes,
                sourceUri.toString(), ref, sourcePath);
    }

    private byte[] downloadArchive(URI uri, int redirectCount) {
        if (redirectCount > properties.getMaxRedirects()) {
            throw new IllegalArgumentException("skill source exceeded redirect limit");
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(properties.getReadTimeoutMillis()))
                .header("Accept", "application/zip, application/octet-stream")
                .header("User-Agent", "mall-order-agent-skill-installer/1.0")
                .GET().build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("location")
                        .orElseThrow(() -> new IllegalArgumentException("skill source redirect has no location"));
                response.body().close();
                return downloadArchive(sourcePolicy.validate(uri.resolve(location)), redirectCount + 1);
            }
            if (status != 200) {
                response.body().close();
                throw new IllegalArgumentException("skill source returned HTTP " + status);
            }
            long declaredLength = response.headers().firstValueAsLong("content-length").orElse(-1);
            if (declaredLength > properties.getMaxArchiveBytes()) {
                response.body().close();
                throw new IllegalArgumentException("skill archive exceeds configured size limit");
            }
            try (InputStream body = response.body()) {
                return readLimited(body, properties.getMaxArchiveBytes(), "skill archive");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to download skill archive", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("skill download was interrupted", e);
        }
    }

    private Map<String, byte[]> extractArchive(byte[] archive) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (files.size() >= properties.getMaxFiles()) {
                    throw new IllegalArgumentException("skill archive contains too many files");
                }
                String name = normalizeArchivePath(entry.getName());
                byte[] content = readLimited(zip, properties.getMaxFileBytes(), "skill file " + name);
                total += content.length;
                if (total > properties.getMaxPackageBytes()) {
                    throw new IllegalArgumentException("skill package exceeds configured extracted size limit");
                }
                if (files.putIfAbsent(name, content) != null) {
                    throw new IllegalArgumentException("skill archive contains duplicate file: " + name);
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("invalid skill ZIP archive", e);
        }
        return files;
    }

    static Map<String, byte[]> selectSkillFiles(Map<String, byte[]> archiveFiles, String sourcePath) {
        String expected = sourcePath.isBlank() ? "SKILL.md" : sourcePath + "/SKILL.md";
        String rootPrefix;
        if (archiveFiles.containsKey(expected)) {
            rootPrefix = sourcePath.isBlank() ? "" : sourcePath + "/";
        } else {
            String suffix = "/" + expected;
            List<String> candidates = archiveFiles.keySet().stream()
                    .filter(name -> name.endsWith(suffix))
                    .toList();
            if (candidates.size() != 1) {
                throw new IllegalArgumentException("skill archive must contain exactly one selected SKILL.md");
            }
            rootPrefix = candidates.get(0).substring(0, candidates.get(0).length() - "SKILL.md".length());
        }
        Map<String, byte[]> selected = new LinkedHashMap<>();
        archiveFiles.forEach((name, content) -> {
            if (name.startsWith(rootPrefix)) {
                String relative = name.substring(rootPrefix.length());
                if (!relative.isBlank()) {
                    selected.put(relative, content);
                }
            }
        });
        if (!selected.containsKey("SKILL.md")) {
            throw new IllegalArgumentException("selected skill has no SKILL.md");
        }
        return selected;
    }

    private static URI toArchiveUri(URI source, String ref) {
        String host = source.getHost().toLowerCase();
        String path = source.getPath();
        if (path.toLowerCase().endsWith(".zip")) {
            return source;
        }
        List<String> segments = new ArrayList<>(List.of(path.split("/")));
        segments.removeIf(String::isBlank);
        if ("github.com".equals(host) && segments.size() == 2) {
            String repository = stripGitSuffix(segments.get(1));
            return URI.create("https://codeload.github.com/" + segments.get(0) + "/" + repository
                    + "/zip/" + encodeSegment(ref));
        }
        if ("gitlab.com".equals(host) && segments.size() >= 2) {
            int last = segments.size() - 1;
            String repository = stripGitSuffix(segments.get(last));
            segments.set(last, repository);
            String projectPath = String.join("/", segments);
            return URI.create("https://gitlab.com/" + projectPath + "/-/archive/"
                    + encodeSegment(ref) + "/" + repository + "-" + encodeSegment(ref.replace('/', '-')) + ".zip");
        }
        throw new IllegalArgumentException("unsupported Git source; use GitHub, GitLab, or an allowlisted ZIP URL");
    }

    private static String validateRef(String ref) {
        if (ref.isBlank() || ref.length() > 200 || ref.contains("..") || ref.contains("\\")
                || ref.startsWith("/") || ref.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("invalid source ref");
        }
        return ref;
    }

    static String normalizeRelativePath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String normalized = normalizeArchivePath(path);
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private static String normalizeArchivePath(String path) {
        if (path == null || path.isBlank() || path.startsWith("/") || path.startsWith("\\")
                || path.indexOf('\0') >= 0 || path.contains("\\")) {
            throw new IllegalArgumentException("skill archive contains an invalid path");
        }
        List<String> parts = new ArrayList<>();
        for (String part : path.split("/")) {
            if (part.isBlank() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                throw new IllegalArgumentException("skill archive path escapes package root");
            }
            parts.add(part);
        }
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("skill archive contains an empty path");
        }
        return String.join("/", parts);
    }

    private static byte[] readLimited(InputStream input, long maxBytes, String label) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IllegalArgumentException(label + " exceeds configured size limit");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String digest(Map<String, byte[]> files) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            files.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey)).forEach(entry -> {
                digest.update(entry.getKey().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(entry.getValue());
                digest.update((byte) 0);
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String stripGitSuffix(String value) {
        return value.endsWith(".git") ? value.substring(0, value.length() - 4) : value;
    }

    private static String encodeSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
