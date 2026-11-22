import type { Incident } from '../api';

const RANK = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 };

// On while some open incident isn't acknowledged. Red if any of them is critical.
export function MasterCaution({ incidents, onShow }: { incidents: Incident[]; onShow: (incident: Incident) => void }) {
  const pending = incidents
    .filter(i => i.status === 'OPEN' && i.acknowledgedAtTick === null)
    .sort((a, b) => RANK[a.priority] - RANK[b.priority] || b.openedAtTick - a.openedAtTick);
  if (!pending.length) {
    return <div className="master off" role="status">Sin alarmas pendientes</div>;
  }
  const warning = pending.some(i => i.priority === 'CRITICAL');
  return (
    <button type="button" className={`master ${warning ? 'warning' : 'caution'}`} onClick={() => onShow(pending[0])}
      aria-label={`${warning ? 'Master warning' : 'Master caution'}: ${pending.length} sin reconocer. Ver ${pending[0].ruleCode}`}>
      <span><span aria-hidden>▲ </span>{warning ? 'MASTER WARNING' : 'MASTER CAUTION'}</span>
      <small>{pending.length} sin reconocer</small>
    </button>
  );
}
