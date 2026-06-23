import type { RoomMembershipStore } from "./redis-stores";

export interface RoomJoinResult {
  canvasId: string;
  roomName: string;
  memberCount: number;
}

export class RoomManager {
  constructor(private readonly membership: RoomMembershipStore) {}

  roomName(canvasId: string): string {
    return `canvas:${canvasId}`;
  }

  async join(canvasId: string, socketId: string): Promise<RoomJoinResult> {
    await this.membership.join(canvasId, socketId);
    const members = await this.membership.members(canvasId);
    return {
      canvasId,
      roomName: this.roomName(canvasId),
      memberCount: members.length
    };
  }

  async leave(canvasId: string, socketId: string): Promise<number> {
    await this.membership.leave(canvasId, socketId);
    const members = await this.membership.members(canvasId);
    return members.length;
  }

  async members(canvasId: string): Promise<string[]> {
    return this.membership.members(canvasId);
  }
}