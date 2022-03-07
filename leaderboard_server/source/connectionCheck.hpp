#pragma once

#include <chrono>
#include <iostream>
#include <mutex>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>

class ConnectionCheck {
private:
	std::unordered_map<std::string, uint64_t> reply_times;
	std::unordered_map<std::string, std::unordered_set<std::string>> uuids;
	std::mutex reply_times_mutex;

public:
	ConnectionCheck();

	bool isConnectionValid(std::string ip);

	void registerConnection(std::string ip, uint64_t time);

	void resetTime(std::string ip, uint64_t time);

	void checkTime(std::string ip, uint64_t time);

	void setUuids(std::string ip, std::unordered_set<std::string> uuids_add);
	void removeUuid(std::string ip, std::string uuid);
	void addUuid(std::string ip, std::string uuid);

	std::vector<std::string> getAllUuids();

	void removeConnection(std::string ip);
};