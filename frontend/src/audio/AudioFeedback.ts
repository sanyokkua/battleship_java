import {
    AUDIO_MASTER_GAIN,
    AUDIO_RECIPES,
    generateDeterministicNoiseSamples,
    type AudioComponent,
    type NoiseRecipe,
    type ToneRecipe,
} from './audioRecipes';

export type AudioParamLike = {
    value?: number;
    setValueAtTime?: (value: number, time: number) => void;
    linearRampToValueAtTime?: (value: number, time: number) => void;
    exponentialRampToValueAtTime?: (value: number, time: number) => void;
};

export type AudioNodeLike = {
    connect: (destination: unknown) => unknown;
    disconnect?: () => void;
};

export type AudioContextLike = {
    state: 'suspended' | 'running' | 'closed' | 'interrupted';
    currentTime: number;
    sampleRate: number;
    destination: unknown;
    resume: () => Promise<void>;
    close: () => Promise<void>;
    createGain: () => AudioNodeLike & { gain: AudioParamLike };
    createOscillator: () => AudioNodeLike & {
        type: string;
        frequency: AudioParamLike;
        start: (when?: number) => void;
        stop: (when?: number) => void;
        onended?: (() => void) | null;
    };
    createBufferSource: () => AudioNodeLike & {
        buffer: unknown;
        start: (when?: number) => void;
        stop: (when?: number) => void;
        onended?: (() => void) | null;
    };
    createBuffer: (channels: number, length: number, sampleRate: number) => {
        getChannelData: (channel: number) => Float32Array;
    };
    createBiquadFilter: () => AudioNodeLike & {
        type: string;
        frequency: AudioParamLike;
    };
};

export type AudioFeedbackPort = {
    unlockFromUserGesture: () => void;
    play: (outcome: keyof typeof AUDIO_RECIPES) => void;
    isEnabled: () => boolean;
    setEnabled: (enabled: boolean) => void;
    dispose: () => void;
};

export type AudioFeedbackLifecyclePort = AudioFeedbackPort & {
    resumeFromVisibility: () => void;
};

export type AudioFeedbackOptions = {
    contextFactory?: () => AudioContextLike | null;
};

function defaultContextFactory(): AudioContextLike | null {
    try {
        if (typeof window === 'undefined') return null;
        const contextWindow = window as Window & {
            webkitAudioContext?: typeof AudioContext;
        };
        const Context = window.AudioContext ?? contextWindow.webkitAudioContext;
        return Context ? new Context() as unknown as AudioContextLike : null;
    } catch {
        return null;
    }
}

function setParam(param: AudioParamLike, method: keyof AudioParamLike, value: number, time: number): void {
    try {
        const setter = param[method];
        if (typeof setter === 'function') {
            setter.call(param, value, time);
        } else {
            param.value = value;
        }
    } catch {
        param.value = value;
    }
}

function connect(source: AudioNodeLike, destination: unknown): void {
    source.connect(destination);
}

function scheduleEnvelope(gain: AudioParamLike, startTime: number, durationSeconds: number, peakGain: number, envelope: ToneRecipe['envelope']): void {
    setParam(gain, 'setValueAtTime', envelope === 'decay' ? peakGain : 0, startTime);
    if (envelope === 'decay') {
        setParam(gain, 'exponentialRampToValueAtTime', 0.0001, startTime + durationSeconds);
    } else {
        setParam(gain, 'linearRampToValueAtTime', peakGain, startTime + Math.min(0.006, durationSeconds / 4));
        setParam(gain, 'linearRampToValueAtTime', 0.0001, startTime + durationSeconds);
    }
}

function scheduleTone(
    context: AudioContextLike,
    masterGain: AudioNodeLike,
    component: ToneRecipe,
    activeNodes: Set<AudioNodeLike>,
): void {
    const oscillator = context.createOscillator();
    const gain = context.createGain();
    const startTime = context.currentTime + component.startTimeMs / 1000;
    const durationSeconds = component.durationMs / 1000;
    oscillator.type = component.waveform;
    setParam(oscillator.frequency, 'setValueAtTime', component.startFrequencyHz, startTime);
    setParam(oscillator.frequency, 'linearRampToValueAtTime', component.endFrequencyHz, startTime + durationSeconds);
    scheduleEnvelope(gain.gain, startTime, durationSeconds, component.peakGain, component.envelope);
    connect(oscillator, gain);
    connect(gain, masterGain);
    oscillator.onended = () => {
        activeNodes.delete(oscillator);
        activeNodes.delete(gain);
    };
    activeNodes.add(oscillator);
    activeNodes.add(gain);
    oscillator.start(startTime);
    oscillator.stop(startTime + durationSeconds);
}

function scheduleNoise(
    context: AudioContextLike,
    masterGain: AudioNodeLike,
    component: NoiseRecipe,
    activeNodes: Set<AudioNodeLike>,
): void {
    const source = context.createBufferSource();
    const gain = context.createGain();
    const startTime = context.currentTime + component.startTimeMs / 1000;
    const durationSeconds = component.durationMs / 1000;
    const sampleCount = Math.max(1, Math.ceil(context.sampleRate * durationSeconds));
    const buffer = context.createBuffer(1, sampleCount, context.sampleRate);
    buffer.getChannelData(0).set(generateDeterministicNoiseSamples(component.seed, sampleCount));
    source.buffer = buffer;
    scheduleEnvelope(gain.gain, startTime, durationSeconds, component.peakGain, 'decay');

    let filter: (AudioNodeLike & {type: string; frequency: AudioParamLike}) | undefined;
    if (component.filter) {
        filter = context.createBiquadFilter();
        filter.type = component.filter.type;
        setParam(filter.frequency, 'setValueAtTime', component.filter.frequencyHz, startTime);
        connect(source, filter);
        connect(filter, gain);
    } else {
        connect(source, gain);
    }
    connect(gain, masterGain);
    source.onended = () => {
        activeNodes.delete(source);
        activeNodes.delete(gain);
        if (filter) activeNodes.delete(filter);
    };
    activeNodes.add(source);
    activeNodes.add(gain);
    if (filter) activeNodes.add(filter);
    source.start(startTime);
    source.stop(startTime + durationSeconds);
}

function scheduleComponent(
    context: AudioContextLike,
    masterGain: AudioNodeLike,
    component: AudioComponent,
    activeNodes: Set<AudioNodeLike>,
): void {
    if (component.kind === 'tone') {
        scheduleTone(context, masterGain, component, activeNodes);
    } else {
        scheduleNoise(context, masterGain, component, activeNodes);
    }
}

export function createAudioFeedback(options: AudioFeedbackOptions = {}): AudioFeedbackLifecyclePort {
    const contextFactory = options.contextFactory ?? defaultContextFactory;
    let context: AudioContextLike | null = null;
    let masterGain: AudioNodeLike & { gain: AudioParamLike } | null = null;
    const activeNodes = new Set<AudioNodeLike>();
    let enabled = true;
    let unavailable = false;
    let disposed = false;

    const ensureContext = (): AudioContextLike | null => {
        if (disposed || unavailable) return null;
        if (context) return context;
        try {
            context = contextFactory();
            if (!context) {
                unavailable = true;
                return null;
            }
            masterGain = context.createGain() as AudioNodeLike & { gain: AudioParamLike };
            setParam(masterGain.gain, 'setValueAtTime', AUDIO_MASTER_GAIN, context.currentTime);
            connect(masterGain, context.destination);
            return context;
        } catch {
            unavailable = true;
            context = null;
            masterGain = null;
            return null;
        }
    };

    const resume = (allowCreation: boolean): void => {
        const audioContext = allowCreation ? ensureContext() : context;
        if (!audioContext || disposed || unavailable || audioContext.state === 'closed') return;
        try {
            void audioContext.resume().catch(() => {
                unavailable = true;
            });
        } catch {
            unavailable = true;
        }
    };

    const port: AudioFeedbackLifecyclePort = {
        unlockFromUserGesture: () => resume(true),
        resumeFromVisibility: () => resume(false),
        play: outcome => {
            if (!enabled || disposed || unavailable || !context || !masterGain || context.state !== 'running') return;
            const recipe = AUDIO_RECIPES[outcome];
            for (const component of recipe.components) {
                try {
                    scheduleComponent(context, masterGain, component, activeNodes);
                } catch {
                    // Optional feedback remains best effort when any node fails.
                }
            }
        },
        isEnabled: () => enabled,
        setEnabled: next => {
            enabled = next;
        },
        dispose: () => {
            if (disposed) return;
            disposed = true;
            for (const node of activeNodes) {
                try {
                    const stoppable = node as AudioNodeLike & {stop?: () => void};
                    stoppable.stop?.();
                } catch {
                    // A node may already have ended or be unavailable.
                }
                try {
                    node.disconnect?.();
                } catch {
                    // Cleanup is intentionally best effort.
                }
            }
            activeNodes.clear();
            try {
                masterGain?.disconnect?.();
            } catch {
                // Cleanup is intentionally best effort.
            }
            if (context) {
                try {
                    void context.close().catch(() => undefined);
                } catch {
                    // Cleanup is intentionally best effort.
                }
            }
            context = null;
            masterGain = null;
        },
    };
    return port;
}
