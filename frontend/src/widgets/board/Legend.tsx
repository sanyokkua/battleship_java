import './Legend.css';

export type LegendLabels = {
    water: string;
    ship: string;
    hit: string;
    miss: string;
    sunk: string;
    noGo?: string;
};

export type LegendProps = {
    withNoGo: boolean;
    labels: LegendLabels;
};

export function Legend({withNoGo, labels}: LegendProps) {
    return (
        <div className="legend">
      <span>
        <i className="i-water" aria-hidden="true"/>
          {labels.water}
      </span>
            <span>
        <i className="i-ship" aria-hidden="true"/>
                {labels.ship}
      </span>
            <span>
        <i className="i-hit" aria-hidden="true"/>
                {labels.hit}
      </span>
            <span>
        <i className="i-miss" aria-hidden="true"/>
                {labels.miss}
      </span>
            <span>
        <i className="i-sunk" aria-hidden="true"/>
                {labels.sunk}
      </span>
            {withNoGo && (
                <span>
          <i className="i-block" aria-hidden="true"/>
                    {labels.noGo}
        </span>
            )}
        </div>
    );
}
