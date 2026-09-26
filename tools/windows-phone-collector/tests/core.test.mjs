import test from "node:test";
import assert from "node:assert/strict";
import { parseDevices, chooseDevice, nextSlot, validPng, confirmsUsb } from "../core.mjs";

test("requires a choice when multiple devices are present and rejects unauthorized targets", () => {
  const devices = parseDevices("List of devices attached\nABC device product:foo model:Mi_10S transport_id:1\nDEF unauthorized usb:2\n");
  assert.equal(devices.length, 2);
  assert.equal(devices[0].serial, "ABC");
  assert.throws(() => chooseDevice(devices), /多台/);
  assert.throws(() => chooseDevice(devices, "DEF"), /unauthorized/);
  assert.equal(chooseDevice(devices, "ABC").serial, "ABC");
  assert.throws(() => chooseDevice([], "ABC"), /未连接/);
});

test("skips missed sampling deadlines instead of issuing a burst or inventing frames", () => {
  assert.equal(nextSlot(1000, 3450, 1000), 3);
  assert.equal(nextSlot(1000, 1900, 1000), 1);
});

test("accepts binary PNG signatures and rejects text or newline-corrupted screenshots", () => {
  assert.equal(validPng(Buffer.from("89504e470d0a1a0a00000000", "hex")), true);
  assert.equal(validPng(Buffer.from("Permission denied")), false);
  assert.equal(validPng(Buffer.from("89504e470d0d0a1a0d0a", "hex")), false);
});
test("accepts Windows unknown path only when USB-only discovery matches selected serial", () => {
  assert.equal(confirmsUsb("unknown\r\n", "ABC", "ABC\r\n"), true);
  assert.equal(confirmsUsb("unknown", "ABC", "DEF"), false);
  assert.equal(confirmsUsb("usb:1", "ABC", ""), true);
  assert.equal(confirmsUsb("unknown", "ABC", ""), false);
});
