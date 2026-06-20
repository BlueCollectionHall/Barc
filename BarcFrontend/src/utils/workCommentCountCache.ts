import { fetchCommentCountByWork } from "@/utils/commentApi.ts";

const resolvedCounts = new Map<string, number>();
const pendingCounts = new Map<string, Promise<number>>();

export function getCachedWorkCommentCount(workId: string): number | undefined {
  return resolvedCounts.get(workId);
}

export async function loadWorkCommentCount(workId: string): Promise<number> {
  const cachedCount = resolvedCounts.get(workId);
  if (cachedCount !== undefined) {
    return cachedCount;
  }

  const pendingCount = pendingCounts.get(workId);
  if (pendingCount) {
    return pendingCount;
  }

  const countLoad = fetchCommentCountByWork(workId)
    .catch(() => 0)
    .then((count) => {
      resolvedCounts.set(workId, count);
      pendingCounts.delete(workId);
      return count;
    });

  pendingCounts.set(workId, countLoad);
  return countLoad;
}

export async function loadWorkCommentCounts(workIds: Array<string>): Promise<Record<string, number>> {
  const uniqueWorkIds = Array.from(new Set(workIds));
  const countEntries = await Promise.all(
    uniqueWorkIds.map(async (workId) => [workId, await loadWorkCommentCount(workId)] as const),
  );

  return Object.fromEntries(countEntries);
}

export function resetWorkCommentCountCacheForTests(): void {
  resolvedCounts.clear();
  pendingCounts.clear();
}
