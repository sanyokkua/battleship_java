/**
 * Typed error thrown by every GameAdapter implementation on failure.
 *
 * HttpGameAdapter populates httpStatus/errorCode from the backend's
 * ExceptionDto{status,errorMessage,errorCode?} response body (errorCode is
 * optional/additive — older/stale backends may omit it). MockGameAdapter may
 * throw this directly to simulate backend validation failures in tests.
 */
export class GameAdapterError extends Error {
    httpStatus?: number;
    errorCode?: string;
    context?: string;

    constructor(message: string, opts?: {
        httpStatus?: number;
        errorCode?: string;
        context?: string;
        cause?: unknown
    }) {
        super(message);
        this.name = "GameAdapterError";
        this.httpStatus = opts?.httpStatus;
        this.errorCode = opts?.errorCode;
        this.context = opts?.context;
        if (opts && "cause" in opts) {
            (this as Error & { cause?: unknown }).cause = opts.cause;
        }

        // Restore prototype chain (needed when targeting ES5-transpiled output / some bundlers).
        Object.setPrototypeOf(this, GameAdapterError.prototype);
    }
}

export function isGameAdapterError(e: unknown): e is GameAdapterError {
    return e instanceof GameAdapterError;
}
