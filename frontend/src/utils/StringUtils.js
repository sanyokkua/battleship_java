function isValidString(stringValue) {
    return stringValue
        && typeof stringValue === "string"
        && stringValue.length > 2;
}

export {isValidString};