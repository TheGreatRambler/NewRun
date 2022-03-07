#include "dataStream.hpp"

DataInputStream::DataInputStream(const uint8_t* data_, const size_t size_)
	: data { data_ }
	, size { size_ } { }

uint16_t DataInputStream::readUnsignedShort() {
	uint16_t res = 0;
	res |= (uint16_t)(data[loc] << 8 | data[loc + 1] << 0);
	loc += 2;
	return res;
}

int32_t DataInputStream::readInt() {
	uint32_t res = 0;
	res |= (data[loc] << 24 | data[loc + 1] << 16 | data[loc + 2] << 8
			| data[loc + 3] << 0);
	loc += 4;
	return res;
}

bool DataInputStream::readBoolean() {
	bool res = data[loc] != 0;
	loc++;
	return res;
}

uint64_t DataInputStream::readLong() {
	uint64_t byte1 = data[loc] & 0x00000000000000FFULL;
	uint64_t byte2 = data[loc + 1] & 0x00000000000000FFULL;
	uint64_t byte3 = data[loc + 2] & 0x00000000000000FFULL;
	uint64_t byte4 = data[loc + 3] & 0x00000000000000FFULL;
	uint64_t byte5 = data[loc + 4] & 0x00000000000000FFULL;
	uint64_t byte6 = data[loc + 5] & 0x00000000000000FFULL;
	uint64_t byte7 = data[loc + 6] & 0x00000000000000FFULL;
	uint64_t byte8 = data[loc + 7] & 0x00000000000000FFULL;

	uint64_t res = (byte1 << 56 | byte2 << 48 | byte3 << 40 | byte4 << 32
					| byte5 << 24 | byte6 << 16 | byte7 << 8 | byte8 << 0);
	loc += 8;
	return res;
}

std::string DataInputStream::readUTF() {
	uint16_t size = readUnsignedShort();

	if(size == 0) {
		return "";
	}

	std::string result(size, 0);

	size_t count = 0;
	size_t index = 0;
	uint8_t a    = 0;

	while(count < size) {
		if((result[index] = data[loc + count++]) < 0x80) {
			index++;
		} else if(((a = result[index]) & 0xE0) == 0xC0) {
			uint8_t b       = data[loc + count++];
			result[index++] = (uint8_t)(((a & 0x1F) << 6) | (b & 0x3F));
		}
	}

	loc += size;

	return result;
}