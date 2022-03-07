#pragma once

#define ASIO_STANDALONE
#define RAPIDJSON_HAS_STDSTRING 1
#define _WEBSOCKETPP_CPP11_THREAD_

#include <websocketpp/config/asio.hpp>
#include <websocketpp/server.hpp>

#include <chrono>
#include <cstdint>
#include <fmt/core.h>
#include <fstream>
#include <memory>
#include <rapidjson/document.h>
#include <rapidjson/stringbuffer.h>
#include <rapidjson/writer.h>
#include <set>
#include <sqlite_modern_cpp.h>
#include <string>
#include <unordered_set>
#include <vector>

#include "connectionCheck.hpp"

class WebsocketServer {
private:
	websocketpp::server<websocketpp::config::asio_tls> endpoint;

	typedef websocketpp::lib::shared_ptr<websocketpp::lib::asio::ssl::context>
		tls_context_ptr;
	typedef std::set<websocketpp::connection_hdl,
		std::owner_less<websocketpp::connection_hdl>>
		con_list;
	con_list connections;
	con_list server_connections;

	sqlite::database& database;
	ConnectionCheck& check;

	void sendJSON(websocketpp::connection_hdl hdl, rapidjson::Document& d);

	uint64_t timeSinceEpochMilliseconds();

	std::string ipFromHdl(websocketpp::connection_hdl hdl);

	std::vector<std::string> splitString(std::string delimiter, std::string s);

public:
	WebsocketServer(sqlite::database& database_, ConnectionCheck& check_);

	void messageHandler(websocketpp::connection_hdl hdl,
		websocketpp::server<websocketpp::config::asio>::message_ptr msg);

	void openHandler(websocketpp::connection_hdl hdl);

	void closeHandler(websocketpp::connection_hdl hdl);

	tls_context_ptr tlsHandler(websocketpp::connection_hdl hdl);

	void run(int port);

	void triggerConnectionCheck();

	void stop();
};