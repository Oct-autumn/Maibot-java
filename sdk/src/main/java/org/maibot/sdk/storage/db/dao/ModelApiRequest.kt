package org.maibot.sdk.storage.db.dao;

import jakarta.persistence.*;

import java.time.Instant;

/// 模型API请求记录表，用于记录每次调用模型API的相关信息
///
/// 字段包括：
///
/// | 字段名 | 类型 | 描述 |
/// | --- | --- | --- |
/// | id | Long | 主键，自增 |
/// | timestamp | Instant | 请求时间戳 |
/// | task_name | String | 任务名称 |
/// | model_name | String | 模型名称，关联模型配置表中的模型名称字段 |
/// | api_provider | String | API提供商名称，关联模型配置表中的API提供商名称字段 |
/// | input_tokens | Long | 输入的token数量 |
/// | output_tokens | Long | 输出的token数量 |
/// | total_cost | Double | 本次调用的总费用，单位为元 |
@Entity
@Table(name = "model_api_request", indexes = {
  @Index(name = "idx_model_api_request_timestamp", columnList = "timestamp"),
  @Index(name = "idx_model_api_request_task_name", columnList = "task_name"),
  @Index(name = "idx_model_api_request_model_name", columnList = "model_name"),
  @Index(name = "idx_model_api_request_api_provider", columnList = "api_provider")
})
public class ModelApiRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /// 请求时间戳
    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    /// 任务名称
    @Column(name = "task_name", nullable = false)
    private String taskName;

    /// 模型名称，关联模型配置表中的模型名称字段
    @Column(name = "model_name", nullable = false)
    private String modelName;

    /// API提供商名称，关联模型配置表中的API提供商名称字段
    @Column(name = "api_provider", nullable = false)
    private String apiProvider;

    /// 输入的token数量
    @Column(name = "input_tokens", nullable = false)
    private Long inputTokens;

    /// 输出的token数量
    @Column(name = "output_tokens", nullable = false)
    private Long outputTokens;

    /// 本次调用的总费用，单位为元
    @Column(name = "total_cost", nullable = false)
    private Double totalCost;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getApiProvider() {
        return apiProvider;
    }

    public void setApiProvider(String apiProvider) {
        this.apiProvider = apiProvider;
    }

    public Long getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(Long inputTokens) {
        this.inputTokens = inputTokens;
    }

    public Long getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(Long outputTokens) {
        this.outputTokens = outputTokens;
    }

    public Double getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(Double totalCost) {
        this.totalCost = totalCost;
    }
}
