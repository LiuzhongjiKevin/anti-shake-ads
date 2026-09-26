export function parseDevices(text) {
  return text.split(/\r?\n/).flatMap((line) => {
    const match = line.match(/^(\S+)\s+(device|unauthorized|offline|recovery|sideload|no permissions)\b(.*)/);
    return match ? [{ serial: match[1], state: match[2], details: match[3].trim() }] : [];
  });
}
export function chooseDevice(devices, serial) {
  if (!serial && devices.length > 1) throw new Error("连接多台设备，请用 --serial 选择目标");
  const device = serial ? devices.find((entry) => entry.serial === serial) : devices[0];
  if (!device) throw new Error("目标设备未连接");
  if (device.state !== "device") throw new Error(`设备状态：${device.state}，请在手机上授权或恢复连接`);
  return device;
}
export function nextSlot(start, end, interval) {
  return Math.floor((end - start) / interval) + 1;
}
export function validPng(bytes) {
  return bytes.length >= 8 && bytes.subarray(0, 8).equals(Buffer.from("89504e470d0a1a0a", "hex"));
}
export function confirmsUsb(devicePath, selected, usbSerial) {
  return devicePath.trim().startsWith("usb:") || (!!selected && usbSerial.trim() === selected);
}
