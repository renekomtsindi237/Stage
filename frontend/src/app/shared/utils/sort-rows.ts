/** Tri client d'une page de tableau (colonne cliquable). */
export function sortRows<T>(rows: T[], key: string, dir: "asc" | "desc"): T[] {
  if (!key) return rows;
  const mul = dir === "asc" ? 1 : -1;
  return [...rows].sort((a, b) => {
    const av = (a as Record<string, unknown>)[key];
    const bv = (b as Record<string, unknown>)[key];
    if (av == null && bv == null) return 0;
    if (av == null) return 1;
    if (bv == null) return -1;
    if (typeof av === "number" && typeof bv === "number") {
      return (av - bv) * mul;
    }
    return String(av).localeCompare(String(bv), "fr", { numeric: true }) * mul;
  });
}
