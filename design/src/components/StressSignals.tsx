/** NERV-style nonsense stress labels — system under strain, not dashboard polish. */
export function StressSignals() {
  return (
    <>
      <div className="mao-stress-chip mao-stress-chip--danger" aria-hidden="true">
        DANGER
      </div>
      <div className="mao-stress-chip mao-stress-chip--emergency" aria-hidden="true">
        EMERGENCY
      </div>
      <div className="mao-stress-chip mao-stress-chip--buffer" aria-hidden="true">
        WARNING: BUFFER OVERFLOW
      </div>
      <div className="mao-stress-chip mao-stress-chip--signal" aria-hidden="true">
        SIGNAL LOST
      </div>
    </>
  );
}
