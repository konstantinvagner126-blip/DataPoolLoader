import { expect, test } from "@playwright/test";

test.use({
  viewport: { width: 1440, height: 1800 }
});

const localCluster = {
  id: "local",
  name: "Local Kafka Docker",
  readOnly: false,
  bootstrapServers: "localhost:19092",
  securityProtocol: "PLAINTEXT"
};

const stageCluster = {
  id: "stage-mtls",
  name: "Stage Kafka mTLS",
  readOnly: true,
  bootstrapServers: "stage-1:9092,stage-2:9092",
  securityProtocol: "SSL"
};

async function prepareKafkaVisualPage(page, path) {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto(path);
  await waitForVisualStyles(page);
  await expect(page.locator(".kafka-page-stack")).toBeVisible();
}

async function waitForVisualStyles(page) {
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForFunction(() => {
    const header = document.querySelector(".kafka-tool-header");
    if (!header) {
      return false;
    }
    const headerStyle = window.getComputedStyle(header);
    const headerRadius = parseFloat(headerStyle.borderTopLeftRadius || "0");
    return headerStyle.display === "grid" && headerRadius >= 8;
  }, null, { timeout: 30000 });
}

async function waitForMonacoEditor(page) {
  await page.waitForFunction(() =>
    Boolean(window.ComposeMonaco?.getEditorValue) && window.ComposeMonaco.getEditorValue() !== null
  );
}

async function applyVisualFrame(page, selector, width, height) {
  await page.addStyleTag({
    content: `
      html { scrollbar-gutter: stable both-edges !important; }
      body { overflow-y: scroll !important; }
      ${selector} {
        width: ${width}px !important;
        min-width: ${width}px !important;
        max-width: ${width}px !important;
        height: ${height}px !important;
        min-height: ${height}px !important;
        max-height: ${height}px !important;
        box-sizing: border-box !important;
        overflow: hidden !important;
      }
    `
  });
  await page.waitForFunction(
    targetSelector => {
      const target = document.querySelector(targetSelector);
      if (!target) {
        return false;
      }
      const fields = Array.from(target.querySelectorAll("input, select, textarea"));
      if (fields.length > 0) {
        return fields.some(field => {
          const style = window.getComputedStyle(field);
          const radius = parseFloat(style.borderTopLeftRadius || "0");
          return radius >= 6 && style.lineHeight !== "normal";
        });
      }
      const cells = Array.from(target.querySelectorAll("th, td"));
      if (cells.length > 0) {
        return cells.some(cell => {
          const style = window.getComputedStyle(cell);
          const padding = parseFloat(style.paddingLeft || "0") + parseFloat(style.paddingRight || "0");
          return padding >= 8 && style.lineHeight !== "normal";
        });
      }
      return true;
    },
    selector,
    { timeout: 30000 }
  );
  await page.waitForFunction(
    ({ targetSelector, expectedWidth, expectedHeight }) => {
      const target = document.querySelector(targetSelector);
      if (!target) {
        return false;
      }
      const rect = target.getBoundingClientRect();
      return Math.round(rect.width) === expectedWidth && Math.round(rect.height) === expectedHeight;
    },
    { targetSelector: selector, expectedWidth: width, expectedHeight: height }
  );
  await page.evaluate(() => {
    if (document.activeElement instanceof HTMLElement) {
      document.activeElement.blur();
    }
  });
}

async function setMonacoEditorValue(page, value) {
  const success = await page.evaluate(nextValue => window.ComposeMonaco.setEditorValue(nextValue), value);
  expect(success).toBe(true);
}

async function mockRuntimeContext(page) {
  await page.route("**/api/ui/runtime-context", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        requestedMode: "files",
        effectiveMode: "files",
        fallbackReason: null,
        actor: {
          resolved: true,
          actorId: "local-user",
          actorSource: "local",
          actorDisplayName: "Local User",
          requiresManualInput: false,
          message: "Локальный пользователь определен."
        },
        database: {
          configured: true,
          available: true,
          schema: "datapool_manual",
          message: "Подключение активно.",
          errorMessage: null
        }
      })
    });
  });
}

async function mockKafkaApi(page, overrides = {}) {
  await page.route("**/api/kafka/info", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(overrides.info ?? {
        configured: true,
        maxRecordsPerRead: 500,
        maxPayloadBytes: 1048576,
        clusters: [localCluster, stageCluster]
      })
    });
  });

  await page.route("**/api/kafka/topics*", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(overrides.topics ?? buildTopicsResponse())
    });
  });

  await page.route("**/api/kafka/topic-overview*", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(overrides.topicOverview ?? buildTopicOverviewResponse())
    });
  });

  await page.route("**/api/kafka/consumer-groups*", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(overrides.consumerGroups ?? buildConsumerGroupsResponse())
    });
  });

  await page.route("**/api/kafka/brokers*", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(overrides.brokers ?? buildBrokersResponse())
    });
  });

  await page.route("**/api/kafka/messages/read", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(overrides.messages ?? buildMessageReadResponse())
    });
  });

  await page.route("**/api/kafka/messages/produce", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        cluster: localCluster,
        topicName: "datapool-test",
        partition: 1,
        offset: 12345,
        timestamp: 1777090000000
      })
    });
  });

  await page.route("**/api/kafka/settings", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(overrides.settings ?? buildSettingsResponse())
    });
  });
}

function buildTopicsResponse() {
  return {
    clusterId: "local",
    query: "",
    topics: [
      {
        name: "datapool-test",
        internal: false,
        partitionCount: 3,
        replicationFactor: 1,
        cleanupPolicy: "delete",
        retentionMs: 3600000,
        retentionBytes: null
      },
      {
        name: "datapool-bulk-50000",
        internal: false,
        partitionCount: 6,
        replicationFactor: 1,
        cleanupPolicy: "delete",
        retentionMs: 86400000,
        retentionBytes: null
      },
      {
        name: "__consumer_offsets",
        internal: true,
        partitionCount: 50,
        replicationFactor: 1,
        cleanupPolicy: "compact",
        retentionMs: null,
        retentionBytes: null
      }
    ]
  };
}

function buildTopicOverviewResponse(consumerGroups = buildTopicConsumerGroups()) {
  return {
    cluster: localCluster,
    topic: {
      name: "datapool-test",
      internal: false,
      partitionCount: 3,
      replicationFactor: 1,
      cleanupPolicy: "delete",
      retentionMs: 3600000,
      retentionBytes: null
    },
    partitions: [
      { partition: 0, leaderId: 1, replicaCount: 1, inSyncReplicaCount: 1, earliestOffset: 0, latestOffset: 1240 },
      { partition: 1, leaderId: 1, replicaCount: 1, inSyncReplicaCount: 1, earliestOffset: 0, latestOffset: 1337 },
      { partition: 2, leaderId: 1, replicaCount: 1, inSyncReplicaCount: 1, earliestOffset: 0, latestOffset: 1198 }
    ],
    consumerGroups
  };
}

function buildTopicConsumerGroups() {
  return {
    status: "AVAILABLE",
    message: null,
    groups: [
      {
        groupId: "datapool-test-group",
        state: "STABLE",
        memberCount: 2,
        metadataAvailable: true,
        totalLag: 7,
        lagStatus: "OK",
        note: null,
        partitions: [
          { partition: 0, committedOffset: 1237, latestOffset: 1240, lag: 3 },
          { partition: 1, committedOffset: 1336, latestOffset: 1337, lag: 1 },
          { partition: 2, committedOffset: 1195, latestOffset: 1198, lag: 3 }
        ]
      }
    ]
  };
}

function buildConsumerGroupsResponse() {
  return {
    cluster: localCluster,
    status: "AVAILABLE",
    message: "One group has partial metadata, but cluster-level catalog is still usable.",
    groups: [
      {
        groupId: "datapool-test-group",
        state: "STABLE",
        memberCount: 2,
        metadataAvailable: true,
        totalLag: 7,
        lagStatus: "OK",
        note: null,
        topics: [
          {
            topicName: "datapool-test",
            partitionCount: 3,
            totalLag: 7,
            partitions: [
              { partition: 0, committedOffset: 1237, latestOffset: 1240, lag: 3 },
              { partition: 1, committedOffset: 1336, latestOffset: 1337, lag: 1 },
              { partition: 2, committedOffset: 1195, latestOffset: 1198, lag: 3 }
            ]
          }
        ]
      },
      {
        groupId: "datapool-audit-group",
        state: "PREPARING_REBALANCE",
        memberCount: 1,
        metadataAvailable: false,
        totalLag: null,
        lagStatus: "UNKNOWN",
        note: "Committed offsets partially unavailable.",
        topics: []
      }
    ]
  };
}

function buildBrokersResponse() {
  return {
    cluster: localCluster,
    controllerBrokerId: 1,
    brokers: [
      { brokerId: 1, host: "localhost", port: 19092, rack: "local-rack-a", controller: true },
      { brokerId: 2, host: "localhost", port: 19093, rack: "local-rack-b", controller: false },
      { brokerId: 3, host: "localhost", port: 19094, rack: "local-rack-c", controller: false }
    ]
  };
}

function buildMessageReadResponse() {
  return {
    cluster: localCluster,
    topicName: "datapool-test",
    scope: "ALL_PARTITIONS",
    partition: null,
    status: "DONE",
    durationMs: 3,
    consumedBytes: 396,
    consumedMessages: 3,
    mode: "LATEST",
    requestedLimit: 3,
    effectiveLimit: 3,
    requestedOffset: null,
    requestedTimestampMs: null,
    effectiveStartOffset: null,
    note: "Merged bounded read across all partitions.",
    records: [
      {
        partition: 1,
        offset: 1337,
        timestamp: 1777090000100,
        key: { sizeBytes: 8, truncated: false, text: "order-42", jsonPrettyText: null },
        value: {
          sizeBytes: 70,
          truncated: false,
          text: "{\"id\":42,\"status\":\"READY\",\"amount\":1250}",
          jsonPrettyText: "{\n  \"id\": 42,\n  \"status\": \"READY\",\n  \"amount\": 1250\n}"
        },
        headers: [
          { name: "source", value: { sizeBytes: 4, truncated: false, text: "test", jsonPrettyText: null } }
        ]
      },
      {
        partition: 0,
        offset: 1240,
        timestamp: 1777090000012,
        key: null,
        value: {
          sizeBytes: 22,
          truncated: false,
          text: "plain text payload",
          jsonPrettyText: null
        },
        headers: []
      },
      {
        partition: 2,
        offset: 1198,
        timestamp: 1777090000210,
        key: { sizeBytes: 5, truncated: false, text: "audit", jsonPrettyText: null },
        value: {
          sizeBytes: 19,
          truncated: false,
          text: "{\"broken\": true",
          jsonPrettyText: null
        },
        headers: [
          { name: "trace-id", value: { sizeBytes: 6, truncated: false, text: "abc-42", jsonPrettyText: null } }
        ]
      }
    ]
  };
}

function buildSettingsResponse() {
  return {
    editableConfigPath: "/Users/kwdev/DataPoolLoader/config/ui-application.yml",
    clusters: [
      {
        id: "local",
        name: "Local Kafka Docker",
        readOnly: false,
        bootstrapServers: "localhost:19092",
        clientId: "datapool-loader",
        securityProtocol: "PLAINTEXT",
        truststoreType: "",
        truststoreLocation: "",
        truststoreCertificates: "",
        keystoreType: "",
        keystoreLocation: "",
        keystoreCertificateChain: "",
        keystoreKey: "",
        keyPassword: "",
        additionalProperties: {
          "client.dns.lookup": "use_all_dns_ips"
        }
      },
      {
        id: "stage-mtls",
        name: "Stage Kafka mTLS",
        readOnly: true,
        bootstrapServers: "stage-1:9092,stage-2:9092",
        clientId: "datapool-loader",
        securityProtocol: "SSL",
        truststoreType: "PEM",
        truststoreLocation: "",
        truststoreCertificates: "${file:/Users/kwdev/certs/stage-ca.crt}",
        keystoreType: "PEM",
        keystoreLocation: "",
        keystoreCertificateChain: "${file:/Users/kwdev/certs/stage-client.crt}",
        keystoreKey: "${file:/Users/kwdev/certs/stage-client.key}",
        keyPassword: "${KAFKA_CLIENT_KEY_PASSWORD}",
        additionalProperties: {}
      }
    ]
  };
}

async function mockKafkaBase(page, overrides = {}) {
  await mockRuntimeContext(page);
  await mockKafkaApi(page, overrides);
}

test("kafka topics catalog targeted visual baseline", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1800 });
  await mockKafkaBase(page);
  await prepareKafkaVisualPage(page, "/kafka?clusterId=local");
  await applyVisualFrame(page, ".kafka-shell", 1416, 760);
  await expect(page.getByText("Topic catalog")).toBeVisible();
  await expect(page.getByText("datapool-bulk-50000")).toBeVisible();
  await expect(page.locator(".kafka-shell")).toHaveScreenshot("kafka-targeted-topics.png", {
    animations: "disabled",
    caret: "hide",
    maxDiffPixels: 512
  });
});

test("kafka create-topic targeted visual baseline", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1800 });
  await mockKafkaBase(page);
  await prepareKafkaVisualPage(page, "/kafka?clusterId=local");
  await page.getByRole("button", { name: "Создать топик" }).click();
  await page.getByPlaceholder("orders.events").fill("datapool-new-events");
  await page.locator(".kafka-topic-create-grid input").nth(1).fill("6");
  await applyVisualFrame(page, ".kafka-topics-panel", 1092, 780);
  await expect(page.locator(".kafka-message-pane-title").filter({ hasText: "Create topic" })).toBeVisible();
  await expect(page.locator(".kafka-topics-panel")).toHaveScreenshot("kafka-targeted-create-topic.png", {
    animations: "disabled",
    caret: "hide",
    maxDiffPixels: 512
  });
});

test("kafka topic overview targeted visual baseline", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1800 });
  await mockKafkaBase(page);
  await prepareKafkaVisualPage(page, "/kafka?clusterId=local&topic=datapool-test");
  await applyVisualFrame(page, ".kafka-topic-details-shell", 1092, 690);
  await expect(page.getByText("Topic overview")).toBeVisible();
  await expect(page.getByText("Partition load")).toBeVisible();
  await expect(page.locator(".kafka-topic-details-shell")).toHaveScreenshot("kafka-targeted-topic-overview.png", {
    animations: "disabled",
    caret: "hide",
    maxDiffPixels: 512
  });
});

test("kafka messages browser targeted visual baseline", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 2200 });
  await mockKafkaBase(page);
  await prepareKafkaVisualPage(
    page,
    "/kafka?clusterId=local&topic=datapool-test&pane=messages&scope=ALL_PARTITIONS"
  );
  await page.getByRole("button", { name: "Читать сообщения" }).click();
  await expect(page.getByText("Merged bounded read across all partitions.")).toBeVisible();
  await page.locator(".kafka-message-expander").nth(0).click();
  await page.locator(".kafka-message-expander").nth(1).click();
  await expect(page.locator(".kafka-message-expanded-row")).toHaveCount(2);
  await page.locator(".kafka-message-control select").nth(1).selectOption("OFFSET");
  await expect(page.getByText("3 messages consumed")).toBeVisible();
  await applyVisualFrame(page, ".kafka-message-panel", 1092, 1240);
  await expect(page.locator(".kafka-message-panel")).toHaveScreenshot("kafka-targeted-messages.png", {
    animations: "disabled",
    caret: "hide",
    maxDiffPixels: 512
  });
});

test("kafka produce targeted visual baseline", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 2200 });
  await mockKafkaBase(page);
  await prepareKafkaVisualPage(page, "/kafka?clusterId=local&topic=datapool-test&pane=produce");
  await waitForMonacoEditor(page);
  await page.locator(".kafka-produce-toolbar input").first().fill("order-42");
  await page.getByRole("button", { name: "Добавить header" }).click();
  await page.locator(".kafka-produce-headers-table input").nth(0).fill("source");
  await page.locator(".kafka-produce-headers-table input").nth(1).fill("visual");
  await setMonacoEditorValue(page, "{\"id\":42,\"status\":\"READY\",\"amount\":1250}");
  await page.getByRole("button", { name: "Отправить сообщение" }).click();
  await expect(page.getByText("Message delivered")).toBeVisible();
  await applyVisualFrame(page, ".kafka-produce-panel", 1092, 1280);
  await expect(page.locator(".kafka-produce-panel")).toHaveScreenshot("kafka-targeted-produce.png", {
    animations: "disabled",
    caret: "hide",
    maxDiffPixels: 512
  });
});

test("kafka consumer groups targeted visual baseline", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1800 });
  await mockKafkaBase(page);
  await prepareKafkaVisualPage(page, "/kafka?clusterId=local&section=consumer-groups");
  await page.getByRole("button", { name: "Показать" }).first().click();
  await expect(page.getByText("datapool-test-group")).toBeVisible();
  await expect(page.getByText("Committed offsets partially unavailable.")).toBeVisible();
  await applyVisualFrame(page, ".kafka-cluster-metadata-panel", 1092, 760);
  await expect(page.locator(".kafka-cluster-metadata-panel")).toHaveScreenshot("kafka-targeted-consumer-groups.png", {
    animations: "disabled",
    caret: "hide",
    maxDiffPixels: 512
  });
});

test("kafka brokers targeted visual baseline", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1800 });
  await mockKafkaBase(page);
  await prepareKafkaVisualPage(page, "/kafka?clusterId=local&section=brokers");
  await applyVisualFrame(page, ".kafka-cluster-metadata-panel", 1092, 760);
  await expect(page.getByText("Broker 1")).toBeVisible();
  await expect(page.locator(".kafka-broker-role-controller").first()).toBeVisible();
  await expect(page.locator(".kafka-cluster-metadata-panel")).toHaveScreenshot("kafka-targeted-brokers.png", {
    animations: "disabled",
    caret: "hide",
    maxDiffPixels: 512
  });
});

test("kafka settings targeted visual baseline", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 2200 });
  await mockKafkaBase(page);
  await prepareKafkaVisualPage(page, "/kafka?clusterId=local&pane=cluster-settings");
  await applyVisualFrame(page, ".kafka-main", 1092, 1280);
  await expect(page.getByText("/Users/kwdev/DataPoolLoader/config/ui-application.yml")).toBeVisible();
  await expect(page.locator(".kafka-settings-cluster-card").filter({ hasText: "Stage Kafka mTLS" })).toBeVisible();
  await expect(page.locator(".kafka-main")).toHaveScreenshot("kafka-targeted-settings.png", {
    animations: "disabled",
    caret: "hide",
    maxDiffPixels: 512
  });
});

test("kafka empty catalog targeted visual baseline", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1200 });
  await mockKafkaBase(page, {
    info: {
      configured: false,
      maxRecordsPerRead: 500,
      maxPayloadBytes: 1048576,
      clusters: []
    }
  });
  await prepareKafkaVisualPage(page, "/kafka");
  await applyVisualFrame(page, ".kafka-page-stack", 1416, 430);
  await expect(page.getByText("Kafka clusters не настроены")).toBeVisible();
  await expect(page.getByRole("button", { name: "Открыть настройки Kafka" })).toBeVisible();
  await expect(page.locator(".kafka-page-stack")).toHaveScreenshot("kafka-targeted-empty.png", {
    animations: "disabled",
    caret: "hide",
    maxDiffPixels: 512
  });
});
