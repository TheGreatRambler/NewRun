#define RAPIDJSON_HAS_STDSTRING 1

#include <chrono>
#include <cpr/cpr.h>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cxxopts.hpp>
#include <fstream>
#include <iostream>
#include <map>
#include <rapidjson/document.h>
#include <rapidjson/stringbuffer.h>
#include <rapidjson/writer.h>
#include <signal.h>
#include <sqlite_modern_cpp.h>
#include <string>
#include <thread>

#include "connectionCheck.hpp"
#include "postServer.hpp"
#include "websocketServer.hpp"

const int websocketPort = 9684;
const int postPort      = 9685;

sqlite::database replayDatabase = sqlite::database("replays.db");
ConnectionCheck connectionCheck;
WebsocketServer websocketServer(replayDatabase, connectionCheck);
PostServer postServer(replayDatabase, connectionCheck);

bool iterate = true;
std::thread websocketThread;
std::thread postThread;
std::thread checkThread;

void runWebsocketThread() {
	std::cout << "Begin websocket thread" << std::endl;
	websocketServer.run(websocketPort);
}

void runPostThread() {
	std::cout << "Begin post thread" << std::endl;
	postServer.run(postPort);
}

void runCheckThread() {
	std::cout << "Begin check thread" << std::endl;
	int i = 0;
	while(iterate) {
		std::this_thread::sleep_for(std::chrono::seconds(1));
		if(i % 30 == 0) {
			websocketServer.triggerConnectionCheck();
		}
		i++;
	}
}

int main(int argc, char* argv[]) {
	signal(SIGINT, [](int) {
		iterate = false;
		websocketServer.stop();
		postServer.stop();
		websocketThread.join();
		postThread.join();
		checkThread.join();
		exit(0);
	});

	cxxopts::Options commandLineOptions("tgrcodeexecutable",
		"Runs in tandem with the site to store various data for retrieval");
	// clang-format off
		commandLineOptions.add_options ()
			("h,help", "Print usage");
	// clang-format on
	cxxopts::ParseResult commandLineResult
		= commandLineOptions.parse(argc, argv);

	if(commandLineResult.count("help")) {
		std::cout << commandLineOptions.help() << std::endl;
		return 0;
	}

	std::cout << "Beginning servers" << std::endl;

	websocketThread = std::thread(runWebsocketThread);
	postThread      = std::thread(runPostThread);
	checkThread     = std::thread(runCheckThread);

	websocketThread.join();
	postThread.join();
	checkThread.join();

	return 0;
}
