package com.tpnube.alert_dispatcher_service.service;

import com.tpnube.alert_dispatcher_service.client.FanIdentityClient;
import com.tpnube.alert_dispatcher_service.client.dto.FanSubscriberDTO;
import com.tpnube.alert_dispatcher_service.dto.MatchEventDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Cubre casos que no se pueden reproducir a mano ejecutando el stack (fallos parciales/totales).
@ExtendWith(MockitoExtension.class)
class AlertDispatchServiceTest {

    @Mock
    private FanIdentityLookupService fanIdentityLookupService;

    @Mock
    private FanIdentityClient fanIdentityClient;

    @Mock
    private DispatcherStatusService dispatcherStatusService;

    private final MatchEventDTO event = new MatchEventDTO(
            "evt-1", "match-001", "team-colon", "GOAL", 10,
            "Gol de Colón", "Facundo Farías", null, 1, 0, Instant.now());

    @Test
    void unFalloAlNotificarUnFanNoImpideNotificarALosDemas() {
        AlertDispatchService service = new AlertDispatchService(
                fanIdentityLookupService, fanIdentityClient, dispatcherStatusService);

        List<FanSubscriberDTO> subscribers = List.of(
                new FanSubscriberDTO("1", "fan1@quienjuega.com"),
                new FanSubscriberDTO("2", "fan2@quienjuega.com"));
        when(fanIdentityLookupService.getSubscribers("team-colon")).thenReturn(subscribers);
        doThrow(new RuntimeException("fan-identity no responde"))
                .when(fanIdentityClient).createNotification(argThat(req -> "1".equals(req.fanId())));

        service.dispatch(event);

        verify(fanIdentityClient, times(1)).createNotification(argThat(req -> "1".equals(req.fanId())));
        verify(fanIdentityClient, times(1)).createNotification(argThat(req -> "2".equals(req.fanId())));
        verify(dispatcherStatusService).recordProcessed(event, 1); // 1 notificado, no 2 suscriptos
    }

    @Test
    void siFallanTodosLosCallbacksSeRegistraComoNoDespachadoYNoComoProcesado() {
        AlertDispatchService service = new AlertDispatchService(
                fanIdentityLookupService, fanIdentityClient, dispatcherStatusService);

        List<FanSubscriberDTO> subscribers = List.of(
                new FanSubscriberDTO("1", "fan1@quienjuega.com"),
                new FanSubscriberDTO("2", "fan2@quienjuega.com"));
        when(fanIdentityLookupService.getSubscribers("team-colon")).thenReturn(subscribers);
        doThrow(new RuntimeException("fan-identity no responde"))
                .when(fanIdentityClient).createNotification(any());

        service.dispatch(event);

        verify(dispatcherStatusService, times(0)).recordProcessed(any(), anyInt());
        verify(dispatcherStatusService).recordUndispatched(eq(event), any());
    }

    @Test
    void unEventoSinSuscriptosNoLlamaAlCallback() {
        AlertDispatchService service = new AlertDispatchService(
                fanIdentityLookupService, fanIdentityClient, dispatcherStatusService);

        when(fanIdentityLookupService.getSubscribers("team-colon")).thenReturn(List.of());

        service.dispatch(event);

        verify(fanIdentityClient, times(0)).createNotification(any());
        verify(dispatcherStatusService).recordProcessed(event, 0);
    }

    @Test
    void unTipoLibreSeDescribeEnElMensajeDeLaAlerta() {
        AlertDispatchService service = new AlertDispatchService(
                fanIdentityLookupService, fanIdentityClient, dispatcherStatusService);
        MatchEventDTO tiroLibre = new MatchEventDTO(
                "evt-2", "match-001", "team-colon", "TIRO_LIBRE", 33, "Lo cobró Colón", null, null, null, null, Instant.now());
        when(fanIdentityLookupService.getSubscribers("team-colon"))
                .thenReturn(List.of(new FanSubscriberDTO("1", "fan1@quienjuega.com")));

        service.dispatch(tiroLibre);

        verify(fanIdentityClient).createNotification(
                argThat(req -> "Tiro libre (33') — Lo cobró Colón".equals(req.message())));
    }

    @Test
    void unEventoSinSuscriptoresDisponiblesSeRegistraComoNoDespachado() {
        AlertDispatchService service = new AlertDispatchService(
                fanIdentityLookupService, fanIdentityClient, dispatcherStatusService);

        when(fanIdentityLookupService.getSubscribers("team-colon"))
                .thenThrow(new FanIdentityLookupService.SubscribersUnavailableException(
                        "team-colon", new RuntimeException("circuito abierto")));

        service.dispatch(event);

        verify(fanIdentityClient, times(0)).createNotification(any());
        verify(dispatcherStatusService).recordUndispatched(eq(event), any());
    }
}
