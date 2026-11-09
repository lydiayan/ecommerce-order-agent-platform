package com.css.mallorderagent.skill;

import com.css.mallorderagent.config.OrderAgentProperties;
import com.css.mallorderagent.skill.model.DownloadedSkillPackage;
import com.css.mallorderagent.skill.model.SkillManifest;
import com.css.mallorderagent.skill.model.SkillPreview;
import com.css.mallorderagent.skill.model.SkillSourceRequest;
import com.css.mallorderagent.skill.model.SkillVersionView;
import com.css.mallorderagent.skill.model.SkillView;
import com.css.mallorderagent.skill.packageinfo.SkillPackageParser;
import com.css.mallorderagent.skill.registry.SkillRepository;
import com.css.mallorderagent.skill.source.SkillSourceClient;
import com.css.mallorderagent.skill.store.SkillPackageStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Service
public class SkillManagementService {

    private final OrderAgentProperties.SkillProperties properties;
    private final SkillSourceClient sourceClient;
    private final SkillPackageParser packageParser;
    private final SkillPackageStore packageStore;
    private final SkillRepository repository;

    public SkillManagementService(OrderAgentProperties properties,
                                  SkillSourceClient sourceClient,
                                  SkillPackageParser packageParser,
                                  SkillPackageStore packageStore,
                                  SkillRepository repository) {
        this.properties = properties.getSkill();
        this.sourceClient = sourceClient;
        this.packageParser = packageParser;
        this.packageStore = packageStore;
        this.repository = repository;
    }

    public SkillPreview preview(SkillSourceRequest request) {
        ensureEnabled();
        DownloadedSkillPackage downloaded = sourceClient.download(request);
        SkillManifest manifest = packageParser.parse(downloaded);
        List<String> fileNames = downloaded.files().keySet().stream().sorted().toList();
        return new SkillPreview(manifest.name(), manifest.description(), manifest.instructions(), downloaded.digest(),
                manifest.requiredActions(), manifest.triggerKeywords(), manifest.allowImplicitInvocation(),
                fileNames, downloaded.files().size(), downloaded.totalBytes());
    }

    public SkillVersionView install(SkillSourceRequest request, String installedBy) {
        ensureEnabled();
        if (request.expectedDigest() == null || request.expectedDigest().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "expectedDigest is required; preview the skill before installation");
        }
        DownloadedSkillPackage downloaded = sourceClient.download(request);
        if (!MessageDigest.isEqual(request.expectedDigest().trim().toLowerCase().getBytes(StandardCharsets.US_ASCII),
                downloaded.digest().getBytes(StandardCharsets.US_ASCII))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "skill package changed after preview; preview it again");
        }
        SkillManifest manifest = packageParser.parse(downloaded);
        String storageKey = packageStore.store(manifest.name(), downloaded.digest(), downloaded.files());
        return repository.install(manifest, downloaded, storageKey, installedBy);
    }

    public List<SkillView> list() {
        return repository.findAll();
    }

    public List<SkillVersionView> versions(long skillId) {
        return repository.findVersions(skillId);
    }

    public void activate(long skillId, long versionId) {
        if (!repository.activate(skillId, versionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "skill or version was not found");
        }
    }

    public void disable(long skillId) {
        if (!repository.disable(skillId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "skill was not found");
        }
    }

    public void updatePolicy(long skillId, boolean allowImplicit) {
        if (!repository.updatePolicy(skillId, allowImplicit)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "skill was not found");
        }
    }

    private void ensureEnabled() {
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "skill installation is disabled");
        }
    }
}
