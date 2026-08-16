import {normalizeAppBasePath} from "./appBasePath";

export const APP_BASE_PATH: string = normalizeAppBasePath(import.meta.env.BASE_URL);
