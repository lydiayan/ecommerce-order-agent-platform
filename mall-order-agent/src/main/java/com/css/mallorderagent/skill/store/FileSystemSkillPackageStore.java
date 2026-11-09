package com.css.mallorderagent.skill.store;

import com.css.mallorderagent.config.OrderAgentProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Map;

@Component
public class FileSystemSkillPackageStore implements SkillPackageStore {

    private final Path root;

    public FileSystemSkillPackageStore(OrderAgentProperties properties) {
        this.root = Path.of(properties.getSkill().getStorageDirectory()).toAbsolutePath().normalize();
    }

    @Override
    public String store(String skillName, String packageDigest, Map<String, byte[]> files) {
        String storageKey = skillName + "/" + packageDigest;
        Path skillRoot = root.resolve(skillName).normalize();
        Path target = root.resolve(storageKey).normalize();
        assertWithin(root, target);
        try {
            Files.createDirectories(skillRoot);
            if (Files.isDirectory(target)) {
                return storageKey;
            }
            Path staging = Files.createTempDirectory(skillRoot, ".staging-");
            try {
                for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                    Path output = staging.resolve(entry.getKey()).normalize();
                    assertWithin(staging, output);
                    Files.createDirectories(output.getParent());
                    Files.write(output, entry.getValue());
                }
                try {
                    Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    moveIfAbsent(staging, target);
                } catch (FileAlreadyExistsException e) {
                    // An identical content-addressed package won a concurrent install.
                }
            } finally {
                deleteStaging(staging);
            }
            return storageKey;
        } catch (IOException e) {
            throw new IllegalStateException("failed to store skill package", e);
        }
    }

    private static void assertWithin(Path root, Path path) {
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("skill package path escapes storage root");
        }
    }

    private static void moveIfAbsent(Path staging, Path target) throws IOException {
        try {
            Files.move(staging, target);
        } catch (FileAlreadyExistsException e) {
            // An identical content-addressed package won a concurrent install.
        }
    }

    private static void deleteStaging(Path staging) throws IOException {
        if (!Files.exists(staging)) {
            return;
        }
        try (var paths = Files.walk(staging)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
