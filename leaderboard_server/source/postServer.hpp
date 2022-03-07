#pragma once

#include <fmt/core.h>
#include <gzip/compress.hpp>
#include <gzip/decompress.hpp>
#include <httplib.h>
#include <sqlite_modern_cpp.h>
#include <string>
#include <utility>
#include <vector>

#include "connectionCheck.hpp"
#include "dataStream.hpp"

class PostServer {
private:
	sqlite::database& database;
	ConnectionCheck& check;
	httplib::Server server;

public:
	PostServer(sqlite::database& database_, ConnectionCheck& check_);

	void run(int port);

	void stop();
};