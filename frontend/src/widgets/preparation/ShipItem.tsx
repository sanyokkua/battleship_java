export type ShipItemData = {
    shipId: string;
    shipSize: number; // ship length in cells, i.e. number of "chip" indicators rendered
    typeName: string; // already-localized ship type name (e.g. "Carrier")
    placed: boolean; // true once the player has placed this ship on their board
    active: boolean; // true when this is the currently-selected ship for placement
};

export type ShipItemProps = {
    ship: ShipItemData;
    cellLabel: string; // already-localized "N cell(s)" text, e.g. "3 cells"
    removeLabel: string; // aria-label for the remove (✕) button
    onSelect: () => void; // invoked when an unplaced ship row is clicked, selects it for placement
    onRemove?: () => void; // invoked when the remove (✕) button on a placed ship is clicked
};

/**
 * One row in the fleet tray: name + size, a strip of cell-count "chips", and,
 * when placed, a ✕ remove button. `active`/`placed` toggle the
 * `.ship-item.active`/`.placed` visual states. Unplaced ships are clickable
 * (select for placement); placed ships show a remove button instead and are
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
