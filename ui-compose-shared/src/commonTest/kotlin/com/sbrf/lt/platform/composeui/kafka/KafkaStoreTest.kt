package com.sbrf.lt.platform.composeui.kafka

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class KafkaStoreTest {
    @Test
    fun `load applies route driven cluster topic pane and message settings`() {
        val store = KafkaStore(StubKafkaApi())

        val state = runKafkaSuspend {
            store.load(
                preferredClusterId = "local",
                preferredClusterSection = "topics",
                preferredTopicName = "datapool-test",
                topicQuery = "data",
                activePane = "messages",
                preferredMessageReadScope = "ALL_PARTITIONS",
                preferredMessageReadMode = "OFFSET",
                preferredMessagePartition = 1,
            )
        }

        assertEquals("local", state.selectedClusterId)
        assertEquals("datapool-test", state.selectedTopicName)
        assertEquals("messages", state.activePane)
        assertEquals("ALL_PARTITIONS", state.messageReadScope)
        assertEquals("OFFSET", state.messageReadMode)
        assertEquals(1, state.selectedMessagePartition)
        assertEquals("50", state.messageLimitInput)
    }

    @Test
    fun `load without explicit topic keeps cluster shell on landing state`() {
        val store = KafkaStore(StubKafkaApi())

        val state = runKafkaSuspend {
            store.load(
                preferredClusterId = "local",
                preferredClusterSection = "consumer-groups",
                preferredTopicName = null,
                activePane = "messages",
            )
        }

        assertEquals("local", state.selectedClusterId)
        assertEquals("consumer-groups", state.clusterSection)
        assertNull(state.selectedTopicName)
        assertNull(state.topicOverview)
        assertEquals("overview", state.activePane)
    }

    @Test
    fun `load keeps consumers tab for explicit topic and keeps cluster settings as dedicated pane`() {
        val store = KafkaStore(StubKafkaApi())

        val topicState = runKafkaSuspend {
            store.load(
                preferredClusterId = "local",
                preferredClusterSection = "topics",
                preferredTopicName = "datapool-test",
                activePane = "consumers",
            )
        }
        val settingsState = runKafkaSuspend {
            store.load(
                preferredClusterId = "local",
                preferredClusterSection = "topics",
                preferredTopicName = "datapool-test",
                activePane = "cluster-settings",
            )
        }

        assertEquals("datapool-test", topicState.selectedTopicName)
        assertEquals("consumers", topicState.activePane)
        assertEquals("cluster-settings", settingsState.activePane)
    }

    @Test
    fun `load cluster consumer groups when section is consumer-groups`() {
        val store = KafkaStore(StubKafkaApi())

        val state = runKafkaSuspend {
            store.load(
                preferredClusterId = "local",
                preferredClusterSection = "consumer-groups",
            )
        }

        assertEquals("consumer-groups", state.clusterSection)
        assertNull(state.selectedTopicName)
        assertNull(state.topicOverview)
        assertEquals("AVAILABLE", state.consumerGroups?.status)
        assertEquals("datapool-test-group", state.consumerGroups?.groups?.single()?.groupId)
    }

    @Test
    fun `load cluster brokers when section is brokers`() {
        val store = KafkaStore(StubKafkaApi())

        val state = runKafkaSuspend {
            store.load(
                preferredClusterId = "local",
                preferredClusterSection = "brokers",
            )
        }

        assertEquals("brokers", state.clusterSection)
        assertNull(state.selectedTopicName)
        assertNull(state.topicOverview)
        assertEquals(1, state.brokers?.controllerBrokerId)
        assertEquals(2, state.brokers?.brokers?.size)
        assertEquals(true, state.brokers?.brokers?.first()?.controller)
    }

    @Test
    fun `read messages in all partitions mode sends topic wide bounded request`() {
        var capturedRequest: KafkaTopicMessageReadRequestPayload? = null
        val store = KafkaStore(
            StubKafkaApi(
                readMessagesHandler = { request ->
                    capturedRequest = request
                    KafkaTopicMessageReadResponse(
                        cluster = sampleKafkaInfo().clusters.first(),
                        topicName = request.topicName,
                        scope = request.scope,
                        partition = request.partition,
                        mode = request.mode,
                        requestedLimit = request.limit ?: 0,
                        effectiveLimit = request.limit ?: 0,
                        records = listOf(
                            KafkaTopicMessageRecordResponse(
                                partition = 1,
                                offset = 17,
                            ),
                            KafkaTopicMessageRecordResponse(
                                partition = 0,
                                offset = 12,
                            ),
                        ),
                    )
                },
            ),
        )

        val loaded = runKafkaSuspend {
            store.load(
                preferredClusterId = "local",
                preferredTopicName = "datapool-test",
                activePane = "messages",
                preferredMessageReadScope = "ALL_PARTITIONS",
                preferredMessageReadMode = "LATEST",
                preferredMessagePartition = 1,
            )
        }
        val prepared = store.updateMessageLimitInput(loaded, "2")

        val updated = runKafkaSuspend { store.readMessages(prepared) }

        assertNotNull(capturedRequest)
        assertEquals("ALL_PARTITIONS", capturedRequest!!.scope)
        assertNull(capturedRequest!!.partition)
        assertEquals(2, capturedRequest!!.limit)
        assertEquals("ALL_PARTITIONS", updated.messages?.scope)
        assertEquals(2, updated.messages?.records?.size)
        assertEquals(1, updated.messages?.records?.first()?.partition)
    }

    @Test
    fun `produce message maps partition key headers and payload through api request`() {
        var capturedRequest: KafkaTopicProduceRequestPayload? = null
        val store = KafkaStore(
            StubKafkaApi(
                produceMessageHandler = { request ->
                    capturedRequest = request
                    KafkaTopicProduceResponse(
                        cluster = sampleKafkaInfo().clusters.first(),
                        topicName = request.topicName,
                        partition = request.partition ?: 0,
                        offset = 42,
                        timestamp = 1_700_000_000_000,
                    )
                },
            ),
        )

        val loaded = runKafkaSuspend {
            store.load(
                preferredClusterId = "local",
                preferredTopicName = "datapool-test",
                activePane = "produce",
            )
        }
        var prepared = store.updateProducePartitionInput(loaded, "1")
        prepared = store.updateProduceKeyInput(prepared, "order-key")
        prepared = store.addProduceHeader(prepared)
        prepared = store.updateProduceHeaderName(prepared, 0, "source")
        prepared = store.updateProduceHeaderValue(prepared, 0, "test")
        prepared = store.addProduceHeader(prepared)
        prepared = store.updateProduceHeaderName(prepared, 1, "trace-id")
        prepared = store.updateProduceHeaderValue(prepared, 1, "abc")
        prepared = store.updateProducePayloadInput(prepared, """{"id":42}""")

        val updated = runKafkaSuspend { store.produceMessage(prepared) }

        assertNotNull(capturedRequest)
        assertEquals(1, capturedRequest!!.partition)
        assertEquals("order-key", capturedRequest!!.keyText)
        assertEquals("""{"id":42}""", capturedRequest!!.payloadText)
        assertEquals(2, capturedRequest!!.headers.size)
        assertEquals("source", capturedRequest!!.headers[0].name)
        assertEquals("test", capturedRequest!!.headers[0].valueText)
        assertEquals(42, updated.produceResult?.offset)
    }

    @Test
    fun `create topic maps topic admin request and refreshes catalog`() {
        var capturedRequest: KafkaTopicCreateRequestPayload? = null
        val store = KafkaStore(
            StubKafkaApi(
                createTopicHandler = { request ->
                    capturedRequest = request
                    KafkaTopicCreateResponse(
                        cluster = sampleKafkaInfo().clusters.first(),
                        topicName = request.topicName,
                        partitionCount = request.partitionCount,
                        replicationFactor = request.replicationFactor,
                        cleanupPolicy = request.cleanupPolicy,
                        retentionMs = request.retentionMs,
                        retentionBytes = request.retentionBytes,
                    )
                },
                loadTopicsHandler = { _, _ ->
                    KafkaTopicsCatalogResponse(
                        clusterId = "local",
                        query = "",
                        topics = listOf(
                            sampleKafkaTopics().topics.first(),
                            KafkaTopicSummaryResponse(
                                name = "orders.events",
                                internal = false,
                                partitionCount = 3,
                                replicationFactor = 2,
                                cleanupPolicy = "compact",
                                retentionMs = 120_000,
                            ),
                        ),
                    )
                },
                loadTopicOverviewHandler = { _, topicName ->
                    sampleKafkaOverview().copy(
                        topic = KafkaTopicSummaryResponse(
                            name = topicName,
                            internal = false,
                            partitionCount = 3,
                            replicationFactor = 2,
                            cleanupPolicy = "compact",
                            retentionMs = 120_000,
                        ),
                    )
                },
            ),
        )

        val loaded = runKafkaSuspend { store.load(preferredClusterId = "local") }
        var prepared = store.toggleCreateTopicForm(loaded)
        prepared = store.updateCreateTopicNameInput(prepared, "orders.events")
        prepared = store.updateCreateTopicPartitionsInput(prepared, "3")
        prepared = store.updateCreateTopicReplicationFactorInput(prepared, "2")
        prepared = store.updateCreateTopicCleanupPolicyInput(prepared, "compact")
        prepared = store.updateCreateTopicRetentionMsInput(prepared, "120000")

        val updated = runKafkaSuspend { store.createTopic(store.startCreateTopic(prepared)) }

        assertEquals("orders.events", capturedRequest?.topicName)
        assertEquals(3, capturedRequest?.partitionCount)
        assertEquals(2, capturedRequest?.replicationFactor)
        assertEquals("compact", capturedRequest?.cleanupPolicy)
        assertEquals(120_000L, capturedRequest?.retentionMs)
        assertEquals("orders.events", updated.selectedTopicName)
        assertEquals("orders.events", updated.topicOverview?.topic?.name)
        assertEquals(false, updated.createTopicFormVisible)
        assertEquals("orders.events", updated.createTopicResult?.topicName)
    }

    @Test
    fun `settings workflow edits tests and saves cluster catalog without losing additional properties`() {
        var capturedConnectionTest: KafkaSettingsConnectionTestRequestPayload? = null
        var capturedSaveRequest: KafkaSettingsUpdateRequestPayload? = null
        val store = KafkaStore(
            StubKafkaApi(
                loadSettingsHandler = {
                    KafkaSettingsResponse(
                        editableConfigPath = "/tmp/ui-application.yml",
                        clusters = listOf(
                            KafkaEditableClusterResponse(
                                id = "local",
                                name = "Local Kafka",
                                readOnly = false,
                                bootstrapServers = "localhost:19092",
                                securityProtocol = "PLAINTEXT",
                                additionalProperties = mapOf("client.dns.lookup" to "use_all_dns_ips"),
                            ),
                        ),
                    )
                },
                testSettingsConnectionHandler = { request ->
                    capturedConnectionTest = request
                    KafkaSettingsConnectionTestResponse(
                        success = true,
                        message = "ok",
                        nodeCount = 1,
                    )
                },
                saveSettingsHandler = { request ->
                    capturedSaveRequest = request
                    KafkaSettingsResponse(
                        editableConfigPath = "/tmp/ui-application.yml",
                        clusters = request.clusters.map {
                            KafkaEditableClusterResponse(
                                id = it.id,
                                name = it.name,
                                readOnly = it.readOnly,
                                bootstrapServers = it.bootstrapServers,
                                clientId = it.clientId,
                                securityProtocol = it.securityProtocol,
                                truststoreType = it.truststoreType,
                                truststoreLocation = it.truststoreLocation,
                                truststoreCertificates = it.truststoreCertificates,
                                keystoreType = it.keystoreType,
                                keystoreLocation = it.keystoreLocation,
                                keystoreCertificateChain = it.keystoreCertificateChain,
                                keystoreKey = it.keystoreKey,
                                keyPassword = it.keyPassword,
                                additionalProperties = it.additionalProperties,
                            )
                        },
                    )
                },
            ),
        )

        val loaded = runKafkaSuspend { store.load(activePane = "cluster-settings") }
        val withSettings = runKafkaSuspend { store.loadSettings(store.startSettingsReload(loaded)) }
        val added = store.addSettingsCluster(withSettings)
        val updatedDraft = store.updateSettingsCluster(added, 1) {
            it.copy(
                id = "stage",
                name = "Stage Kafka",
                readOnly = true,
                bootstrapServers = "stage-1:9092,stage-2:9092",
                securityProtocol = "SSL",
                truststoreType = "PEM",
                truststoreCertificates = "\${file:/tmp/ca.crt}",
            )
        }
        val tested = runKafkaSuspend {
            store.testSettingsConnection(
                store.startSettingsConnectionTest(updatedDraft),
                clusterIndex = 1,
            )
        }
        val saved = runKafkaSuspend { store.saveSettings(store.startSettingsSave(tested)) }

        assertEquals("stage", capturedConnectionTest?.cluster?.id)
        assertEquals("stage-1:9092,stage-2:9092", capturedConnectionTest?.cluster?.bootstrapServers)
        assertEquals("use_all_dns_ips", capturedSaveRequest?.clusters?.first()?.additionalProperties?.get("client.dns.lookup"))
        assertEquals(2, capturedSaveRequest?.clusters?.size)
        assertEquals("Настройки Kafka сохранены.", saved.settingsStatusMessage)
        assertEquals("stage", saved.settings?.clusters?.last()?.id)
        assertNull(saved.settingsConnectionResult)
    }
}
