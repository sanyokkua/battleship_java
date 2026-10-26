export function isStackReady(output: string, origin: string) {
    return output.includes(`[stack] ready at ${origin}`);
}
