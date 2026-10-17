package com.example.boilerplate.crm.settings;

import com.example.boilerplate.common.entity.WorkspaceOwnedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "pipeline_stages")
public class PipelineStage extends WorkspaceOwnedEntity {
  @Column(nullable = false, length = 60)
  private String name;

  @Column(nullable = false)
  private int position;

  @JdbcTypeCode(SqlTypes.SMALLINT)
  @Column(nullable = false)
  private int probability;

  // Won and Lost come only from WorkspaceDefaults; the API creates open stages.
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 8, updatable = false)
  private StageKind kind = StageKind.OPEN;

  protected PipelineStage() {}

  public PipelineStage(String name, int probability) {
    this.name = name;
    this.probability = probability;
  }

  public void update(String name, int probability) {
    this.name = name;
    this.probability = probability;
  }

  void moveTo(int position) {
    this.position = position;
  }

  public String getName() {
    return name;
  }

  public int getPosition() {
    return position;
  }

  public int getProbability() {
    return probability;
  }

  public StageKind getKind() {
    return kind;
  }
}
