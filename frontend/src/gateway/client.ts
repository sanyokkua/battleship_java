import createClient from 'openapi-fetch';
import type { paths } from '../contract/generated/api';
import { readCsrfToken } from './csrf';

export const client = createClient<paths>({ baseUrl: '', credentials: 'same-origin' });
client.use({
    onRequest({ request }) {
        if (request.method === 'POST') {
            const token = readCsrfToken();
            if (token) request.headers.set('X-XSRF-TOKEN', token);
        }
        return request;
    },
});
