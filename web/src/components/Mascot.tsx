import wave from "../assets/mascot/wave.webp";
import guide from "../assets/mascot/guide.webp";
import think from "../assets/mascot/think.webp";
import cheer from "../assets/mascot/cheer.webp";

const POSES = { wave, guide, think, cheer };
export type MascotPose = keyof typeof POSES;

export function Mascot({ pose = "wave", size = 100, className = "" }: {
  pose?: MascotPose;
  size?: number;
  className?: string;
}) {
  return <img src={POSES[pose]} alt="" aria-hidden="true" draggable={false}
    width={size} height={size} className={`shrink-0 object-contain select-none ${className}`}
    style={{ width: size, height: size }} />;
}
