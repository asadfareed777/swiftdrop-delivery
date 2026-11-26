package com.asadfareed.swiftdrop.data

import com.asadfareed.swiftdrop.core.OrderStatus
import com.asadfareed.swiftdrop.core.TrackingEvent
import com.asadfareed.swiftdrop.data.tracking.TrackingProtocol
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Messages exactly as server/tracking-server.js sends them. */
class TrackingProtocolTest {

    @Test
    fun `decodes status, location and eta messages`() {
        assertThat(TrackingProtocol.decode("""{"type":"status","status":"PICKED_UP","at":42}"""))
            .isEqualTo(TrackingEvent.StatusChanged(OrderStatus.PICKED_UP, 42))
        assertThat(TrackingProtocol.decode("""{"type":"location","x":450.5,"y":300,"bearing":90,"at":43}"""))
            .isEqualTo(TrackingEvent.CourierMoved(450.5, 300.0, 90.0, 43))
        assertThat(TrackingProtocol.decode("""{"type":"eta","seconds":240,"at":44}"""))
            .isEqualTo(TrackingEvent.EtaUpdated(240, 44))
    }

    @Test
    fun `unknown fields are ignored for forward compatibility`() {
        assertThat(TrackingProtocol.decode("""{"type":"eta","seconds":5,"at":1,"traffic":"heavy"}"""))
            .isEqualTo(TrackingEvent.EtaUpdated(5, 1))
    }

    @Test
    fun `malformed or unknown messages are dropped instead of crashing`() {
        assertThat(TrackingProtocol.decode("not json")).isNull()
        assertThat(TrackingProtocol.decode("""{"type":"weather","at":1}""")).isNull()
        assertThat(TrackingProtocol.decode("""{"type":"status","status":"TELEPORTED","at":1}""")).isNull()
        assertThat(TrackingProtocol.decode("""{"type":"location","x":1,"at":1}""")).isNull()
    }
}
