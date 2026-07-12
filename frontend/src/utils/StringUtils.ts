function isValidString(stringValue: string | null): boolean {
    return Boolean(stringValue && stringValue.length > 2);
}

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function isValidUuid(value: string | null): boolean {
    return Boolean(value && UUID_REGEX.test(value.trim()));
}

export {isValidString, isValidUuid};