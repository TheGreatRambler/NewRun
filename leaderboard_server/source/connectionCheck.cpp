#include "connectionCheck.hpp"

ConnectionCheck::ConnectionCheck() { }

bool ConnectionCheck::isConnectionValid(std::string ip) {
	std::scoped_lock lock(reply_times_mutex);
	return reply_times.count(ip) && reply_times[ip] != 0;
}

void ConnectionCheck::registerConnection(std::string ip, uint64_t time) {
	std::scoped_lock lock(reply_times_mutex);
	reply_times[ip] = time;
}

void ConnectionCheck::resetTime(std::string ip, uint64_t time) {
	std::scoped_lock lock(reply_times_mutex);
	if(std::chrono::milliseconds(time - reply_times[ip])
		< std::chrono::seconds(30)) {
		reply_times[ip] = time;
	} else {
		// Marked invalid
		reply_times[ip] = 0;
	}
}

void ConnectionCheck::checkTime(std::string ip, uint64_t time) {
	std::scoped_lock lock(reply_times_mutex);
	reply_times[ip] = time;
}

void ConnectionCheck::setUuids(
	std::string ip, std::unordered_set<std::string> uuids_add) {
	uuids[ip] = uuids_add;
}

void ConnectionCheck::removeUuid(std::string ip, std::string uuid) {
	if(uuids.count(ip)) {
		uuids[ip].erase(uuid);
	}
}

void ConnectionCheck::addUuid(std::string ip, std::string uuid) {
	if(uuids.count(ip)) {
		uuids[ip].insert(uuid);
	}
}

std::vector<std::string> ConnectionCheck::getAllUuids() {
	std::vector<std::string> allUuids;
	for(auto& server : uuids) {
		for(std::string uuid : server.second) {
			allUuids.push_back(uuid);
		}
	}
	return allUuids;
}

void ConnectionCheck::removeConnection(std::string ip) {
	std::scoped_lock lock(reply_times_mutex);
	reply_times.erase(ip);
	uuids.erase(ip);
}