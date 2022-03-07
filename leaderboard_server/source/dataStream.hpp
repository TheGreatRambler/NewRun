#pragma once

#include <cstdint>
#include <cstring>
#include <iostream>
#include <string>

class DataInputStream {
private:
	const uint8_t* data;
	const size_t size;
	size_t loc = 0;

public:
	DataInputStream(const uint8_t* data_, const size_t size_);

	uint16_t readUnsignedShort();
	int32_t readInt();
	bool readBoolean();
	uint64_t readLong();
	std::string readUTF();
};