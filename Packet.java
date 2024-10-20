class Packet {
    int sequenceNumber;
    byte[] data;

    public Packet(int sequenceNumber, byte[] data) {
        this.sequenceNumber = sequenceNumber;
        this.data = data;
    }
}
