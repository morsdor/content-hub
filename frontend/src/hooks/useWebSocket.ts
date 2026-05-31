// Phase 1: replace with real WebSocket connection per docs/05 §3
// WS protocol: wss://api.contenthub.app/v1/ws?token=<jwt>
// Messages: subscribe, crdt.update, presence.cursor, board.changed, job.completed

export function useWebSocket(_workspaceId: string) {
  return { connected: false, lastMessage: null as null };
}
