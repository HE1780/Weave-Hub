package com.iflytek.skillhub.config;

import com.iflytek.skillhub.domain.agent.AgentMetadataParser;
import com.iflytek.skillhub.domain.agent.AgentPackageValidator;
import com.iflytek.skillhub.domain.agent.AgentVisibilityChecker;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.domain.skill.metadata.SkillMetadataParser;
import com.iflytek.skillhub.domain.skill.validation.SkillPackageValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wires application-level Spring beans that adapt configurable infrastructure into domain-facing
 * ports.
 */
@Configuration
public class DomainBeanConfig {

    @Bean
    public Clock utcClock() {
        return Clock.systemUTC();
    }

    @Bean
    public SkillMetadataParser skillMetadataParser() {
        return new SkillMetadataParser();
    }

    @Bean
    public SkillPackageValidator skillPackageValidator(SkillMetadataParser skillMetadataParser,
                                                       SkillPublishProperties skillPublishProperties) {
        return new SkillPackageValidator(
                skillMetadataParser,
                skillPublishProperties.getMaxFileCount(),
                skillPublishProperties.getMaxSingleFileSize(),
                skillPublishProperties.getMaxPackageSize(),
                skillPublishProperties.getAllowedFileExtensions()
        );
    }

    @Bean
    public VisibilityChecker visibilityChecker() {
        return new VisibilityChecker();
    }

    @Bean
    public AgentMetadataParser agentMetadataParser() {
        return new AgentMetadataParser();
    }

    @Bean
    public AgentPackageValidator agentPackageValidator(AgentMetadataParser agentMetadataParser) {
        return new AgentPackageValidator(agentMetadataParser);
    }

    @Bean
    public AgentVisibilityChecker agentVisibilityChecker() {
        return new AgentVisibilityChecker();
    }
}
