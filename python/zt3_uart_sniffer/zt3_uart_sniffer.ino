/*
 * ZT3 UART Sniffer — Arduino Hardware-USART
 *
 * Nutzt den echten ATmega328P-USART-Chip (Pin D0=RX, D1=TX) statt
 * SoftwareSerial. Saubere 115200-Decoding, kein Bit-Banging.
 *
 * Single-USART-Trick: derselbe USART bedient die USB-Bridge UND D0/D1.
 * Wir lesen vom Roller via D0 → ATmega328P RX → forward via Serial.write
 * → ATmega16U2 → USB → Mac. Beide Seiten laufen mit identischer Baudrate.
 *
 * Verkabelung:
 *   D0  → Roller-Bus (grün)         [Hardware-RX]
 *   D1  → unbenutzt                  [Hardware-TX, KEIN Anschluss]
 *   GND → Roller-schwarz / Frame
 *
 * EMPFOHLEN: 1k Widerstand in Serie zwischen grün und D0.
 *   - begrenzt Strom falls ATmega16U2-TX (5V idle-high) gegen 3.3V-Bus drückt
 *   - schützt sowohl Scooter-MCU als auch Arduino-Pin
 *
 * Ablauf:
 *   1. Sketch hochladen MIT Roller getrennt (USB allein an Arduino)
 *   2. Erst NACH Upload den Pierce auf grün → D0 stecken
 *      (Während Upload würde D0 kollidieren mit USB-bridge)
 *   3. Sniffer Mac-seitig: uart_sniff.py /dev/cu.usbmodem21101 115200
 */

void setup() {
  Serial.begin(115200);
  // Nichts weiter — die UART-RX-Pipeline läuft automatisch im Hintergrund,
  // empfangene Bytes werden bei jedem Serial.write() zurück über USB geschickt.
}

void loop() {
  while (Serial.available()) {
    Serial.write(Serial.read());
  }
}
