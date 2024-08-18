package com.tianji.remark.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "tj.remark")
public class LikedBizTypeConfig {
    private List<String> bizTypes;

}