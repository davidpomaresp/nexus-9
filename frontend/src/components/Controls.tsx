import { useRef, useState, type FormEvent, type ReactNode } from 'react';
import type { useSimulation } from '../useSimulation';

interface Props {
  station: ReturnType<typeof useSimulation>;
  scenarios: string[];
  children?: ReactNode;
}

const SPEEDS = [1, 5, 20];

export function Controls({ station, scenarios, children }: Props) {
  const [scenario, setScenario] = useState('solar-degradation');
  const [seed, setSeed] = useState('42');
  const [seedError, setSeedError] = useState<string | null>(null);
  const [chosenSpeed, setSpeed] = useState(5);
  // A ref, not only state: a quick double click can fire twice before the button re-renders disabled.
  const creating = useRef(false);
  const [busy, setBusy] = useState(false);
  const ready = station.sim !== null;
  const running = station.sim?.running ?? false;
  const speed = running ? station.sim!.speed : chosenSpeed;

  function submit(e: FormEvent) {
    e.preventDefault();
    const value = Number(seed);
    // Larger integers lose precision as JSON numbers in JavaScript.
    if (seed.trim() === '' || !Number.isSafeInteger(value)) {
      setSeedError('La semilla debe ser un entero de hasta 15 cifras');
      return;
    }
    setSeedError(null);
    if (creating.current) return;
    creating.current = true;
    setBusy(true);
    void station.create(value, scenario).finally(() => {
      creating.current = false;
      setBusy(false);
    });
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
        <button type="submit" className="primary" disabled={busy}>Nueva simulación</button>
        {seedError && <span className="field-error">{seedError}</span>}
      </form>

      {children}
      <div className="run">
        <button type="button" className="primary" disabled={!ready}
          onClick={() => void (running ? station.pause() : station.run(speed))}>
          {running ? '❚❚ Pausa' : '▶ Ejecutar'}
        </button>
        <div className="segmented" role="group" aria-label="Ticks cada 250 ms">
          {SPEEDS.map(s => (
            <button key={s} type="button" className={speed === s ? 'on' : ''} disabled={!ready}
              onClick={() => (running ? void station.run(s) : setSpeed(s))}>×{s}</button>
          ))}
        </div>
        <button type="button" disabled={!ready} onClick={() => void station.advance(10)}>+10</button>
        <button type="button" disabled={!ready} onClick={() => void station.advance(100)}>+100</button>
      </div>
    </div>
  );
}
