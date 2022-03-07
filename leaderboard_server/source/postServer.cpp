#include "postServer.hpp"

PostServer::PostServer(sqlite::database& database_, ConnectionCheck& check_)
	: database { database_ }
	, check { check_ } {
	database << "create table if not exists replay ("
				"   name TEXT,"
				"   uploaded INTEGER,"
				"   version INTEGER,"
				"   minecraft_version TEXT,"
				"   completed INTEGER,"
				"   length INTEGER,"
				"   overworld TEXT,"
				"   nether TEXT,"
				"   end TEXT,"
				"   overworld_seed INTEGER,"
				"   nether_seed INTEGER,"
				"   end_seed INTEGER,"
				"   starting_dimension INTEGER,"
				"   generation_type TEXT,"
				"   size INTEGER,"
				"   data BLOB"
				");";

	database << "create table if not exists replay_players ("
				"   name TEXT,"
				"   player TEXT"
				");";

	database << "create table if not exists replay_gameplay_types ("
				"   name TEXT,"
				"   gameplay_type TEXT"
				");";
}

void PostServer::run(int port) {
	server.Post("/newrun/upload_replay", [&](const auto& req, auto&) {
		std::cout << "Recieved post from " << req.remote_addr << std::endl;
		if(check.isConnectionValid(req.remote_addr) && req.has_file("file")) {
			std::cout << "Validated post from " << req.remote_addr << std::endl;
			const auto& file = req.get_file_value("file");

			DataInputStream input_stream(
				(const uint8_t*)file.content.c_str(), file.content.size());

			std::string replayName       = input_stream.readUTF();
			uint64_t uploaded            = input_stream.readLong();
			int replayVersion            = input_stream.readInt();
			std::string minecraftVersion = input_stream.readUTF();
			bool hasCompleted            = input_stream.readBoolean();
			uint64_t length              = input_stream.readLong();
			std::string overworldName    = input_stream.readUTF();
			std::string netherName       = input_stream.readUTF();
			std::string endName          = input_stream.readUTF();
			uint64_t overworldSeed       = input_stream.readLong();
			uint64_t netherSeed          = input_stream.readLong();
			uint64_t endSeed             = input_stream.readLong();
			int startingDimension        = input_stream.readInt();
			std::string generationType   = input_stream.readUTF();

			// clang-format off
			std::cout << "Recieved replay " << replayName << std::endl;
			std::cout << "    uploaded           " << uploaded << std::endl;
			std::cout << "    version            " << replayVersion << std::endl;
			std::cout << "    minecraft_version  " << minecraftVersion << std::endl;
			std::cout << "    completed          " << (hasCompleted ? "TRUE" : "FALSE") << std::endl;
			std::cout << "    length             " << length << std::endl;
			std::cout << "    overworld          " << overworldName << std::endl;
			std::cout << "    nether             " << netherName << std::endl;
			std::cout << "    end                " << endName << std::endl;
			std::cout << "    overworld_seed     " << overworldSeed << std::endl;
			std::cout << "    nether_seed        " << netherSeed << std::endl;
			std::cout << "    end_seed           " << endSeed << std::endl;
			std::cout << "    starting_dimension " << startingDimension << std::endl;
			std::cout << "    generation_type    " << generationType << std::endl;
			std::cout << "    size               " << file.content.size() << std::endl;
			// clang-format on

			std::vector<std::string> gameplayTypes;
			const int gameplayTypesSize = input_stream.readInt();
			if(gameplayTypesSize != 0) {
				std::cout << "    gameplay_types" << std::endl;
			} else {
				std::cout << "    gameplay_types     none" << std::endl;
			}
			for(int i = 0; i < gameplayTypesSize; i++) {
				std::string type = input_stream.readUTF();
				gameplayTypes.push_back(type);
				std::cout << "        " << type << std::endl;
			}

			std::cout << "    players" << std::endl;
			std::vector<std::string> playerUuids;
			const int playerUuidsSize = input_stream.readInt();
			for(int i = 0; i < playerUuidsSize; i++) {
				std::string uuid = input_stream.readUTF();
				playerUuids.push_back(uuid);
				std::cout << "        " << uuid << std::endl;
			}

			std::string compressed = gzip::compress(
				file.content.c_str(), file.content.size(), Z_BEST_COMPRESSION);
			database
				<< "insert into replay values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);"
				<< replayName << uploaded << replayVersion << minecraftVersion
				<< (int)hasCompleted << length << overworldName << netherName
				<< endName << overworldSeed << netherSeed << endSeed
				<< startingDimension << generationType << file.content.size()
				<< std::vector<uint8_t>(compressed.begin(), compressed.end());

			for(auto& gameplayType : gameplayTypes) {
				database << "insert into replay_gameplay_types values (?,?);"
						 << replayName << gameplayType;
			}

			for(auto& player : playerUuids) {
				database << "insert into replay_players values (?,?);"
						 << replayName << player;
			}
		}
	});

	server.Get("/newrun/download_replay", [&](const auto& req, auto& res) {
		if(check.isConnectionValid(req.remote_addr)
			&& req.has_header("replay_name")) {
			const std::string name = req.get_header_value("replay_name");
			std::cout << "Download replay " << name << " from "
					  << req.remote_addr << std::endl;
			std::vector<uint8_t> compressed;
			database << "select data from replay where name=?;" << name
				>> compressed;
			std::string decompressed = gzip::decompress(
				(const char*)compressed.data(), compressed.size());
			res.set_content(decompressed.data(), decompressed.size(),
				"application/octet-stream");
		}
	});

	server.listen("0.0.0.0", port);
}

void PostServer::stop() {
	server.stop();
}