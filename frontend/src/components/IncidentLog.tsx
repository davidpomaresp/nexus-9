import { useRef, useState, type MouseEvent } from 'react';
import type { Incident } from '../api';

const RULE_LABEL: Record<string, string> = {
  GENERATION_DEFICIT: 'Generación por debajo del 70 %',
  COMMS_DEGRADED: 'Comunicaciones en ahorro',
  BATTERY_LOW: 'Batería por debajo del 20 %',
  BATTERY_CRITICAL: 'Batería por debajo del 5 %',
  OXYGEN_LOW: 'Oxígeno por debajo del 95 %',
};

interface Props {
  incidents: Incident[];
  currentTick: number;
  focused: string | null;
  onFocus: (incident: Incident) => void;
  onAcknowledge: (incident: Incident) => Promise<boolean>;
}

export function IncidentLog({ incidents, currentTick, focused, onFocus, onAcknowledge }: Props) {
  const rows = useRef(new Map<string, HTMLButtonElement>());
  const [said, setSaid] = useState('');

  async function acknowledge(i: Incident, e: MouseEvent<HTMLButtonElement>) {
    const hadFocus = document.activeElement === e.currentTarget;
    if (!(await onAcknowledge(i))) return;
    setSaid(`${i.ruleCode} reconocida`);
    // the button disappears, so keep the keyboard on the same incident
    if (hadFocus) rows.current.get(i.id)?.focus();
  }

  if (!incidents.length) return <p className="muted">Sin incidencias.</p>;
  const sorted = [...incidents].sort((a, b) =>
    (a.status === b.status ? 0 : a.status === 'OPEN' ? -1 : 1) || b.openedAtTick - a.openedAtTick);
  return (
    <>
    <p className="sr-only" aria-live="polite">{said}</p>
    <ol className="incident-list">
      {sorted.map(i => {
        const end = i.resolvedAtTick ?? currentTick;
        return (
          <li key={i.id} className="incident-row">
            <button type="button" aria-pressed={i.id === focused} onClick={() => onFocus(i)}
              ref={el => { if (el) rows.current.set(i.id, el); else rows.current.delete(i.id); }}
              className={`incident p-${i.priority} ${i.status === 'OPEN' ? 'open' : 'resolved'}`}>
            <span className="prio">{i.priority}</span>
            <div className="what">
              <strong>{i.ruleCode}</strong>
              <span>{RULE_LABEL[i.ruleCode] ?? ''}</span>
            </div>
            <div className="when">
              <span>{i.module}{i.affectedModules.length ? ` → ${i.affectedModules.join(', ')}` : ''}</span>
              <span>
                t={i.openedAtTick} · {i.status === 'OPEN' ? `abierta hace ${end - i.openedAtTick} min`
                  : `resuelta en t=${i.resolvedAtTick} (${end - i.openedAtTick} min)`}
              </span>
            </div>
            </button>
            {i.status === 'OPEN' && i.acknowledgedAtTick === null ? (
              <button type="button" className="ack" onClick={e => void acknowledge(i, e)}
                aria-label={`Reconocer ${i.ruleCode}`}>Reconocer</button>
            ) : i.acknowledgedAtTick !== null ? (
              <span className="acked">reconocida t={i.acknowledgedAtTick}</span>
            ) : null}
          </li>
        );
      })}
    </ol>
    </>
  );
}
