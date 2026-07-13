export type ShipItemData = {
    shipId: string;
    shipSize: number;
    typeName: string;
    placed: boolean;
    active: boolean;
};

export type ShipItemProps = {
    ship: ShipItemData;
    cellLabel: string; // already-localized "N cell(s)" text, e.g. "3 cells"
    removeLabel: string; // aria-label for the remove (✕) button
    onSelect: () => void;
    onRemove?: () => void;
};

/**
 * One row in the fleet tray — ported from MOCKUP.html's `.ship-item` (name + size,
 * a strip of cell-count "chips", and, when placed, a ✕ remove button). `active`/`placed`
 * toggle the mockup's `.ship-item.active`/`.placed` visual states. Unplaced ships are
 * clickable (select for placement); placed ships show a remove button instead and are
 * not clickable for selection.
 */
export function ShipItem({ship, cellLabel, removeLabel, onSelect, onRemove}: ShipItemProps) {
    const classes = ['ship-item', ship.active ? 'active' : '', ship.placed ? 'placed' : ''].filter(Boolean).join(' ');
    const chips = Array.from({length: ship.shipSize});

    const content = (
        <>
            <div className="nm">
                {ship.typeName}
                <small>{cellLabel}</small>
            </div>
            <div className="mid">
                <div className={`ship-cells${ship.placed ? '' : ' dim'}`} aria-hidden="true">
                    {chips.map((_, i) => (
                        <i key={i}/>
                    ))}
                </div>
                {ship.placed && onRemove && (
                    <button
                        type="button"
                        className="remove-btn"
                        aria-label={removeLabel}
                        onClick={(e) => {
                            e.stopPropagation();
                            onRemove();
                        }}
                    >
                        ✕
                    </button>
                )}
            </div>
        </>
    );

    if (ship.placed) {
        return <div className={classes}>{content}</div>;
    }

    return (
        <button type="button" className={classes} onClick={onSelect}>
            {content}
        </button>
    );
}
