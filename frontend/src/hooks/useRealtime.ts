import { Client } from '@stomp/stompjs';
import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { toast } from 'sonner';
import { tokenStore } from '../api/client';

interface PushNotification {
  id: number; eventType: string; title: string; message: string; incidentId?: number; incidentNumber?: string;
}

const URGENT = new Set(['SLA_BREACHED', 'INCIDENT_ESCALATED', 'MAJOR_INCIDENT_DETECTED']);

/**
 * Maintains an authenticated STOMP-over-WebSocket connection:
 *  - private notifications -> toast + refresh inbox
 *  - incident change feed  -> refresh incident lists, queues and dashboards (debounced)
 */
export function useRealtime(enabled: boolean, onOpenIncident: (id: number) => void) {
  const queryClient = useQueryClient();
  const [connected, setConnected] = useState(false);
  const openRef = useRef(onOpenIncident);
  openRef.current = onOpenIncident;

  useEffect(() => {
    if (!enabled) return;
    const token = tokenStore.get();
    if (!token) return;

    let refreshTimer: ReturnType<typeof setTimeout> | undefined;
    const scheduleRefresh = () => {
      clearTimeout(refreshTimer);
      refreshTimer = setTimeout(() => {
        queryClient.invalidateQueries({ queryKey: ['incidents'] });
        queryClient.invalidateQueries({ queryKey: ['incident'] });
        queryClient.invalidateQueries({ queryKey: ['queue'] });
        queryClient.invalidateQueries({ queryKey: ['dashboard'] });
        queryClient.invalidateQueries({ queryKey: ['intelligence'] });
      }, 400);
    };

    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
    const defaultWsUrl = `${protocol}://${window.location.host}/ws`;
    const brokerURL = (import.meta.env.VITE_WS_URL as string | undefined) || defaultWsUrl;
    const client = new Client({
      brokerURL,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      heartbeatIncoming: 20000,
      heartbeatOutgoing: 20000,
      onConnect: () => {
        setConnected(true);
        client.subscribe('/user/queue/notifications', (frame) => {
          const n = JSON.parse(frame.body) as PushNotification;
          queryClient.invalidateQueries({ queryKey: ['notifications'] });
          const show = URGENT.has(n.eventType) ? toast.error : toast.info;
          show(n.title, {
            description: n.incidentNumber ? `${n.incidentNumber} · ${n.message}` : n.message,
            action: n.incidentId ? { label: 'Open', onClick: () => openRef.current(n.incidentId!) } : undefined,
            duration: URGENT.has(n.eventType) ? 10000 : 6000,
          });
        });
        client.subscribe('/topic/incidents', scheduleRefresh);
      },
      onWebSocketClose: () => setConnected(false),
      onStompError: () => setConnected(false),
    });
    client.activate();
    return () => {
      clearTimeout(refreshTimer);
      setConnected(false);
      void client.deactivate();
    };
  }, [enabled, queryClient]);

  return connected;
}
