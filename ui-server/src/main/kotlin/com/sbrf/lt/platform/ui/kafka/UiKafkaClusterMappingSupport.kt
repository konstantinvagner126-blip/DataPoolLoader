package com.sbrf.lt.platform.ui.kafka

import com.sbrf.lt.datapool.kafka.KafkaClusterCatalogEntry
import com.sbrf.lt.platform.ui.config.UiKafkaClusterConfig
import com.sbrf.lt.platform.ui.config.bootstrapServers
import com.sbrf.lt.platform.ui.config.securityProtocolOrDefault

internal fun UiKafkaClusterConfig.toCatalogEntry(): KafkaClusterCatalogEntry =
    KafkaClusterCatalogEntry(
        id = id,
        name = name,
        readOnly = readOnly,
        bootstrapServers = bootstrapServers().orEmpty(),
        securityProtocol = securityProtocolOrDefault(),
    )
