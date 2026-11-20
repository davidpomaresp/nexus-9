import { useState, type FormEvent } from 'react';
import type { useSimulation } from '../useSimulation';

interface Props {
  station: ReturnType<typeof useSimulation>;
  scenarios: string[];
}

const SPEEDS = [1, 5, 20];

export function Controls({ station, scenarios }: Props) {
  const [scenario, setScenario] = useState('solar-degradation');
  const [seed, setSeed] = useState('42');
  const [seedError, setSeedError] = useState<string | null>(null);
  const ready = station.sim !== null;

  function submit(e: FormEvent) {
    e.preventDefault();
    const value = Number(seed);
    // Larger integers lose precision as JSON numbers in JavaScript.
    if (seed.trim() === '' || !Number.isSafeInteger(value)) {
      setSeedError('La semilla debe ser un entero de hasta 15 cifras');
      return;
    }
    setSeedError(null);
    void station.create(value, scenario);
  }

  return (
    <div className="panel controls">
      <form onSubmit={submit} className="setup">
        <label>
          <span>Escenario</span>
          <select value={scenario} onChange={e => setScenario(e.target.value)}>
            {(scenarios.length ? scenarios : [scenario]).map(s => <option key={s}>{s}</option>)}
          </select>
        </label>
        <label>
          <span>Semilla</span>
          <input value={seed} inputMode="numeric" onChange={e => setSeed(e.target.value)}
            aria-invalid={seedError !== null} />
        </label>
        <button type="submit" className="primary">Nueva simulación</button>
        {seedError && <span className="field-error">{seedError}</span>}
      </form>

      <div className="run">
        <button type="button" className="primary" disabled={!ready}
          onClick={() => station.setRunning(!station.running)}>
          {station.running ? '❚❚ Pausa' : '▶ Ejecutar'}
        </button>
        <div className="segmented" role="group" aria-label="Ticks por paso">
          {SPEEDS.map(s => (
            <button key={s} type="button" className={station.speed === s ? 'on' : ''}
              disabled={!ready} onClick={() => station.setSpeed(s)}>×{s}</button>
          ))}
        </div>
        <button type="button" disabled={!ready} onClick={() => void station.advance(10)}>+10</button>
        <button type="button" disabled={!ready} onClick={() => void station.advance(100)}>+100</button>
      </div>
    </div>
  );
}
