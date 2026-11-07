import { registerSW } from 'virtual:pwa-register';
import { createPwaGateway, type PwaGateway } from './pwaGateway';

export function registerPwa(): PwaGateway {
    return createPwaGateway(registerSW);
}
