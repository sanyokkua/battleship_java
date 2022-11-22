function isValidString(stringValue: string | null): boolean {
    return Boolean(stringValue && stringValue.length > 2);
}

export {isValidString};