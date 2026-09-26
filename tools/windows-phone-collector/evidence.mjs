export function urlsFromLine(line) {
  return [...line.matchAll(/\b[a-z][a-z0-9+.-]*:\/\/[^\s<>"'\]}]+/gi)].map((match) => match[0]);
}
export function resumedComponent(text) {
  const match = text.match(/(?:topResumedActivity|mResumedActivity)[^\n]*?\bu\d+\s+([A-Za-z0-9_.]+\/[A-Za-z0-9_.$]+)/);
  return match ? match[1] : null;
}
