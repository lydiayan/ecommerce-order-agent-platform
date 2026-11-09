package com.css.mallorderagent.skill;

import com.css.mallorderagent.config.OrderAgentProperties;
import com.css.mallorderagent.skill.model.DownloadedSkillPackage;
import com.css.mallorderagent.skill.model.SkillManifest;
import com.css.mallorderagent.skill.model.SkillSourceRequest;
import com.css.mallorderagent.skill.model.SkillVersionView;
import com.css.mallorderagent.skill.packageinfo.SkillPackageParser;
import com.css.mallorderagent.skill.registry.SkillRepository;
import com.css.mallorderagent.skill.source.SkillSourceClient;
import com.css.mallorderagent.skill.store.SkillPackageStore;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillManagementServiceTest {

    private final SkillSourceClient sourceClient = mock(SkillSourceClient.class);
    private final SkillPackageParser parser = mock(SkillPackageParser.class);
    private final SkillPackageStore store = mock(SkillPackageStore.class);
    private final SkillRepository repository = mock(SkillRepository.class);
    private final SkillManagementService service = new SkillManagementService(
            new OrderAgentProperties(), sourceClient, parser, store, repository);

    @Test
    void installRequiresPreviewDigestToMatchDownloadedContent() {
        String digest = "a".repeat(64);
        SkillSourceRequest request = new SkillSourceRequest(
                "https://github.com/example/skills", "main", "refund", "b".repeat(64));
        when(sourceClient.download(request)).thenReturn(downloaded(digest));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.install(request, "admin"));

        assertEquals(409, error.getStatusCode().value());
    }

    @Test
    void installStoresValidatedPackageThenPersistsVersion() {
        String digest = "a".repeat(64);
        SkillSourceRequest request = new SkillSourceRequest(
                "https://github.com/example/skills", "main", "refund", digest);
        DownloadedSkillPackage downloaded = downloaded(digest);
        SkillManifest manifest = new SkillManifest("refund-explainer", "desc", "instructions",
                List.of(), List.of("退款"), true);
        SkillVersionView version = new SkillVersionView(2, 1, digest, request.sourceUrl(), "main",
                "refund", "refund-explainer/" + digest, List.of(), List.of("退款"),
                "admin", LocalDateTime.now());
        when(sourceClient.download(request)).thenReturn(downloaded);
        when(parser.parse(downloaded)).thenReturn(manifest);
        when(store.store(manifest.name(), digest, downloaded.files()))
                .thenReturn("refund-explainer/" + digest);
        when(repository.install(manifest, downloaded, "refund-explainer/" + digest, "admin"))
                .thenReturn(version);

        assertEquals(version, service.install(request, "admin"));
        verify(repository).install(manifest, downloaded, "refund-explainer/" + digest, "admin");
    }

    private static DownloadedSkillPackage downloaded(String digest) {
        return new DownloadedSkillPackage(Map.of("SKILL.md", new byte[]{1}), digest, 1,
                "https://github.com/example/skills", "main", "refund");
    }
}
