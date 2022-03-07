#include "websocketServer.hpp"

void WebsocketServer::sendJSON(
	websocketpp::connection_hdl hdl, rapidjson::Document& d) {
	rapidjson::StringBuffer buffer;
	rapidjson::Writer<rapidjson::StringBuffer> writer(buffer);
	writer.SetMaxDecimalPlaces(2);
	d.Accept(writer);
	endpoint.send(hdl, buffer.GetString(), websocketpp::frame::opcode::text);
}

uint64_t WebsocketServer::timeSinceEpochMilliseconds() {
	return std::chrono::duration_cast<std::chrono::milliseconds>(
		std::chrono::system_clock::now().time_since_epoch())
		.count();
}

std::string WebsocketServer::ipFromHdl(websocketpp::connection_hdl hdl) {
	return endpoint.get_con_from_hdl(hdl)
		->get_raw_socket()
		.remote_endpoint()
		.address()
		.to_string()
		.substr(7);
}

std::vector<std::string> WebsocketServer::splitString(
	std::string delimiter, std::string s) {
	std::vector<std::string> parts;

	size_t pos = 0;
	std::string token;
	while((pos = s.find(delimiter)) != std::string::npos) {
		token = s.substr(0, pos);
		parts.push_back(token);
		s.erase(0, pos + delimiter.length());
	}

	return parts;
}

WebsocketServer::WebsocketServer(
	sqlite::database& database_, ConnectionCheck& check_)
	: database { database_ }
	, check { check_ } {
	endpoint.set_error_channels(websocketpp::log::elevel::rerror);
	endpoint.set_access_channels(websocketpp::log::alevel::none);
	endpoint.clear_access_channels(websocketpp::log::alevel::frame_payload);

	endpoint.set_message_handler(
		websocketpp::lib::bind(&WebsocketServer::messageHandler, this,
			std::placeholders::_1, std::placeholders::_2));
	endpoint.set_open_handler(websocketpp::lib::bind(
		&WebsocketServer::openHandler, this, std::placeholders::_1));
	endpoint.set_close_handler(websocketpp::lib::bind(
		&WebsocketServer::closeHandler, this, std::placeholders::_1));
	endpoint.set_tls_init_handler(websocketpp::lib::bind(
		&WebsocketServer::tlsHandler, this, std::placeholders::_1));

	endpoint.init_asio();
}

void WebsocketServer::messageHandler(websocketpp::connection_hdl hdl,
	websocketpp::server<websocketpp::config::asio>::message_ptr msg) {
	rapidjson::Document d;

	rapidjson::Document returnJson;
	returnJson.SetObject();

	std::string ip = ipFromHdl(hdl);

	d.Parse(msg->get_payload());
	if(d.IsObject()) {
		std::string flagRecieved = d["flag"].GetString();

		if(flagRecieved == "is_server") {
			server_connections.insert(hdl);
			std::cout << ip << " is server" << std::endl;
			endpoint.send(hdl, "{\"flag\":\"get_uuids\"}",
				websocketpp::frame::opcode::text);
			check.registerConnection(ip, timeSinceEpochMilliseconds());
		}

		if(flagRecieved == "pong") {
			std::cout << "Recieved connection check from " << ip << std::endl;
			check.checkTime(ip, timeSinceEpochMilliseconds());
		}

		if(flagRecieved == "send_uuids") {
			if(d.HasMember("uuids") && d["uuids"].IsArray()) {
				std::unordered_set<std::string> uuids;
				for(auto& uuid : d["uuids"].GetArray()) {
					if(uuid.IsString()) {
						uuids.insert(uuid.GetString());
					}
				}
				check.setUuids(ip, uuids);
			}
		}

		if(flagRecieved == "send_uuid") {
			if(d.HasMember("uuid") && d["uuid"].IsString()) {
				std::cout << "Added player " << d["uuid"].GetString()
						  << std::endl;
				check.addUuid(ip, d["uuid"].GetString());
			}
		}

		if(flagRecieved == "remove_uuid") {
			if(d.HasMember("uuid") && d["uuid"].IsString()) {
				std::cout << "Removed player " << d["uuid"].GetString()
						  << std::endl;
				check.removeUuid(ip, d["uuid"].GetString());
			}
		}

		if(flagRecieved == "request_online") {
			auto& allocator = returnJson.GetAllocator();
			rapidjson::Value players(rapidjson::kArrayType);
			for(std::string player : check.getAllUuids()) {
				players.PushBack(
					rapidjson::Value(player.c_str(), allocator), allocator);
			}

			returnJson.AddMember("flag", "recieve_online", allocator);
			returnJson.AddMember("players", players, allocator);
			sendJSON(hdl, returnJson);
		}

		if(flagRecieved == "request_records") {
			try {
				if(d.HasMember("name") && d["name"].IsString()) {
					std::string name = d["name"].GetString();
					database
							<< "select uploaded,version,minecraft_version,completed,length,overworld,nether,end,overworld_seed,nether_seed,end_seed,starting_dimension,generation_type from replay where name=? limit 1;"
							<< name
						>>
						[&](uint64_t uploaded, int version,
							std::string minecraft_version, int completed,
							uint64_t length, std::string overworld,
							std::string nether, std::string end,
							uint64_t overworld_seed, uint64_t nether_seed,
							uint64_t end_seed, int starting_dimension,
							std::string generation_type) {
							auto& allocator = returnJson.GetAllocator();
							rapidjson::Value players(rapidjson::kArrayType);
							database
									<< "select player from replay_players where name=?;"
									<< name
								>> [&](std::string player) {
									  players.PushBack(
										  rapidjson::Value(
											  player.c_str(), allocator),
										  allocator);
								  };
							rapidjson::Value gameplay_types(
								rapidjson::kArrayType);
							database
									<< "select gameplay_type from replay_gameplay_types where name=?;"
									<< name
								>> [&](std::string gameplay_type) {
									  gameplay_types.PushBack(
										  rapidjson::Value(
											  gameplay_type.c_str(), allocator),
										  allocator);
								  };

							returnJson.AddMember(
								"flag", "recieve_one_replay", allocator);

							returnJson.AddMember("uploaded",
								rapidjson::Value(uploaded), allocator);
							returnJson.AddMember("version",
								rapidjson::Value(version), allocator);
							returnJson.AddMember("minecraft_version",
								rapidjson::Value(
									minecraft_version.c_str(), allocator),
								allocator);
							returnJson.AddMember("completed",
								rapidjson::Value(completed ? true : false),
								allocator);
							returnJson.AddMember(
								"length", rapidjson::Value(length), allocator);
							returnJson.AddMember("overworld",
								rapidjson::Value(overworld.c_str(), allocator),
								allocator);
							returnJson.AddMember("nether",
								rapidjson::Value(nether.c_str(), allocator),
								allocator);
							returnJson.AddMember("end",
								rapidjson::Value(end.c_str(), allocator),
								allocator);
							returnJson.AddMember("overworld_seed",
								rapidjson::Value(overworld_seed), allocator);
							returnJson.AddMember("nether_seed",
								rapidjson::Value(nether_seed), allocator);
							returnJson.AddMember("end_seed",
								rapidjson::Value(end_seed), allocator);
							std::string startingDimension;
							switch(starting_dimension) {
							case 0:
								startingDimension = "overworld";
								break;
							case 1:
								startingDimension = "nether";
								break;
							case 2:
								startingDimension = "end";
								break;
							}
							returnJson.AddMember("starting_dimension",
								rapidjson::Value(
									startingDimension.c_str(), allocator),
								allocator);
							returnJson.AddMember("generation_type",
								rapidjson::Value(
									generation_type.c_str(), allocator),
								allocator);
							returnJson.AddMember("players", players, allocator);
							returnJson.AddMember(
								"gameplay_types", gameplay_types, allocator);

							sendJSON(hdl, returnJson);
						};
				} else {
					if(d.HasMember("page") && d["page"].IsUint()) {
						int page            = d["page"].GetUint();
						const int page_size = 1000;

						uint64_t uploaded_start = 0;
						uint64_t uploaded_end   = 0;
						if(d.HasMember("uploaded_start")
							&& d["uploaded_start"].IsUint64()) {
							uploaded_start = d["uploaded_start"].GetUint64();
							if(d.HasMember("uploaded_end")
								&& d["uploaded_end"].IsUint64()) {
								uploaded_end = d["uploaded_end"].GetUint64();
							}
						}

						std::string minecraft_version;
						if(d.HasMember("minecraft_version")
							&& d["minecraft_version"].IsString()) {
							std::string temp
								= d["minecraft_version"].GetString();
							if(temp.find_first_of("\t\n(), ")
								== std::string::npos) {
								minecraft_version = temp;
							}
						}

						int completed = -1;
						if(d.HasMember("completed")
							&& d["completed"].IsBool()) {
							completed = d["completed"].GetBool() ? 1 : 0;
						}

						uint64_t length_start = 0;
						uint64_t length_end   = 0;
						if(d.HasMember("length_start")
							&& d["length_start"].IsUint64()) {
							length_start = d["length_start"].GetUint64();
							if(d.HasMember("length_end")
								&& d["length_end"].IsUint64()) {
								length_end = d["length_end"].GetUint64();
							}
						}

						bool seed_set = false;
						uint64_t seed = 0;
						if(d.HasMember("seed") && d["seed"].IsUint64()) {
							seed     = d["seed"].GetUint64();
							seed_set = true;
						}

						std::string generation_type;
						if(d.HasMember("generation_type")
							&& d["generation_type"].IsString()) {
							std::string temp = d["generation_type"].GetString();
							if(temp.find_first_of("\t\n(), ")
								== std::string::npos) {
								generation_type = temp;
							}
						}

						std::vector<std::string> contains_players;
						if(d.HasMember("contains_players")
							&& d["contains_players"].IsArray()) {
							for(auto& player :
								d["generation_type"].GetArray()) {
								if(player.IsString()) {
									std::string temp = player.GetString();
									if(temp.find_first_of("\t\n(), ")
										== std::string::npos) {
										contains_players.push_back(temp);
									}
								}
							}
						}

						std::vector<std::string> contains_gameplay_types;
						if(d.HasMember("contains_gameplay_types")
							&& d["contains_gameplay_types"].IsArray()) {
							for(auto& gameplay_type :
								d["contains_gameplay_types"].GetArray()) {
								if(gameplay_type.IsString()) {
									std::string temp
										= gameplay_type.GetString();
									if(temp.find_first_of("\t\n(), ")
										== std::string::npos) {
										contains_gameplay_types.push_back(temp);
									}
								}
							}
						}

						std::string query_string
							= "select name,uploaded,version,minecraft_version,completed,length,overworld,nether,end,overworld_seed,nether_seed,end_seed,starting_dimension,generation_type from replay where";

						if(contains_players.size() != 0) {
							std::string player_query;
							for(const auto& player : contains_players) {
								player_query += fmt::format(
									"select name from replay_players where player=\"{}\" intersect ",
									player);
							}
							player_query.resize(player_query.size() - 11);

							query_string += fmt::format(
								" name in ({}) and", player_query);
						}

						if(contains_gameplay_types.size() != 0) {
							std::string gameplay_type_query;
							for(const auto& gameplay_type :
								contains_gameplay_types) {
								gameplay_type_query += fmt::format(
									"select name from replay_gameplay_types where gameplay_type=\"{}\" intersect ",
									gameplay_type);
							}
							gameplay_type_query.resize(
								gameplay_type_query.size() - 11);

							query_string += fmt::format(
								" name in ({}) and", gameplay_type_query);
						}

						if(uploaded_start != 0 && uploaded_end != 0
							&& uploaded_start <= uploaded_end) {
							query_string += fmt::format(
								" uploaded between {} and {} and",
								uploaded_start, uploaded_end);
						}

						if(!minecraft_version.empty()) {
							query_string
								+= fmt::format(" minecraft_version in ({}) and",
									minecraft_version);
						}

						if(completed != -1) {
							query_string
								+= fmt::format(" completed={} and", completed);
						}

						if(length_start != 0 && length_end != 0
							&& length_start <= length_end) {
							query_string
								+= fmt::format(" length between {} and {} and",
									length_start, length_end);
						}

						if(seed_set) {
							query_string += fmt::format(" seed={} and", seed);
						}

						if(!generation_type.empty()) {
							query_string += fmt::format(
								" generation_type=\"{}\" and", generation_type);
						}

						if(query_string.at(query_string.size() - 1) == 'e') {
							query_string.resize(query_string.size() - 6);
						} else {
							query_string.resize(query_string.size() - 4);
						}

						auto& allocator = returnJson.GetAllocator();
						rapidjson::Value replays(rapidjson::kArrayType);

						database << fmt::format(
							"{} order by uploaded desc limit {} offset {};",
							query_string, page_size, page * page_size)
							>>
							[&](std::string name, uint64_t uploaded,
								int version, std::string minecraft_version,
								int completed, uint64_t length,
								std::string overworld, std::string nether,
								std::string end, uint64_t overworld_seed,
								uint64_t nether_seed, uint64_t end_seed,
								int starting_dimension,
								std::string generation_type) {
								rapidjson::Value elementJson(
									rapidjson::kObjectType);
								rapidjson::Value players(rapidjson::kArrayType);
								std::unordered_set<std::string> players_set;
								database
										<< "select player from replay_players where name=?;"
										<< name
									>> [&](std::string player) {
										  players.PushBack(
											  rapidjson::Value(
												  player.c_str(), allocator),
											  allocator);
										  players_set.insert(player);
									  };
								rapidjson::Value gameplay_types(
									rapidjson::kArrayType);
								std::unordered_set<std::string>
									gameplay_types_set;
								database
										<< "select gameplay_type from replay_gameplay_types where name=?;"
										<< name
									>> [&](std::string gameplay_type) {
										  gameplay_types.PushBack(
											  rapidjson::Value(
												  gameplay_type.c_str(),
												  allocator),
											  allocator);
										  gameplay_types_set.insert(
											  gameplay_type);
									  };
								elementJson.AddMember("name",
									rapidjson::Value(name.c_str(), allocator),
									allocator);
								elementJson.AddMember("uploaded",
									rapidjson::Value(uploaded), allocator);
								elementJson.AddMember("version",
									rapidjson::Value(version), allocator);
								elementJson.AddMember("minecraft_version",
									rapidjson::Value(
										minecraft_version.c_str(), allocator),
									allocator);
								elementJson.AddMember("completed",
									rapidjson::Value(completed ? true : false),
									allocator);
								elementJson.AddMember("length",
									rapidjson::Value(length), allocator);
								elementJson.AddMember("overworld",
									rapidjson::Value(
										overworld.c_str(), allocator),
									allocator);
								elementJson.AddMember("nether",
									rapidjson::Value(nether.c_str(), allocator),
									allocator);
								elementJson.AddMember("end",
									rapidjson::Value(end.c_str(), allocator),
									allocator);
								elementJson.AddMember("overworld_seed",
									rapidjson::Value(overworld_seed),
									allocator);
								elementJson.AddMember("nether_seed",
									rapidjson::Value(nether_seed), allocator);
								elementJson.AddMember("end_seed",
									rapidjson::Value(end_seed), allocator);
								std::string startingDimension;
								switch(starting_dimension) {
								case 0:
									startingDimension = "overworld";
									break;
								case 1:
									startingDimension = "nether";
									break;
								case 2:
									startingDimension = "end";
									break;
								}
								elementJson.AddMember("starting_dimension",
									rapidjson::Value(
										startingDimension.c_str(), allocator),
									allocator);
								elementJson.AddMember("generation_type",
									rapidjson::Value(
										generation_type.c_str(), allocator),
									allocator);
								elementJson.AddMember(
									"players", players, allocator);
								elementJson.AddMember("gameplay_types",
									gameplay_types, allocator);

								replays.PushBack(elementJson, allocator);
							};

						returnJson.AddMember(
							"flag", "recieve_multiple_replays", allocator);
						returnJson.AddMember("page", page, allocator);
						returnJson.AddMember("replays", replays, allocator);
						sendJSON(hdl, returnJson);
					}
				}
			} catch(sqlite::sqlite_exception& e) {
				std::cout << "Incorrect SQL statement from " << ip << " "
						  << e.get_sql() << std::endl;
			}
		}
	} else {
		std::cout << "Recieved invalid JSON from " << ip << std::endl;
	}
}

void WebsocketServer::openHandler(websocketpp::connection_hdl hdl) {
	std::string ip = ipFromHdl(hdl);
	std::cout << "Recieved connection " << ip << std::endl;
	connections.insert(hdl);
}

void WebsocketServer::closeHandler(websocketpp::connection_hdl hdl) {
	std::string ip = ipFromHdl(hdl);
	std::cout << "Disconnected connection " << ip << std::endl;
	connections.erase(hdl);
	if(server_connections.count(hdl)) {
		check.removeConnection(ip);
		server_connections.erase(hdl);
	}
}

WebsocketServer::tls_context_ptr WebsocketServer::tlsHandler(
	websocketpp::connection_hdl hdl) {
	tls_context_ptr ctx;
	try {
		ctx = websocketpp::lib::make_shared<asio::ssl::context>(
			asio::ssl::context::tlsv12);
		ctx->set_options(
			asio::ssl::context::default_workarounds
			| asio::ssl::context::no_sslv2 | asio::ssl::context::no_sslv3
			| asio::ssl::context::no_tlsv1 | asio::ssl::context::single_dh_use);

		std::ifstream config_file("config.json");
		std::string config_string((std::istreambuf_iterator<char>(config_file)),
			std::istreambuf_iterator<char>());
		rapidjson::Document config_object;
		config_object.Parse(config_string.c_str());

		ctx->use_certificate_chain_file(
			config_object["certificate"].GetString());
		ctx->use_private_key_file(config_object["priv_certificate"].GetString(),
			asio::ssl::context::pem);
		ctx->use_tmp_dh_file("dh.pem");

		std::string ciphers
			= "ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES256-GCM-SHA384:ECDHE-ECDSA-AES256-GCM-SHA384:DHE-RSA-AES128-GCM-SHA256:DHE-DSS-AES128-GCM-SHA256:kEDH+AESGCM:ECDHE-RSA-AES128-SHA256:ECDHE-ECDSA-AES128-SHA256:ECDHE-RSA-AES128-SHA:ECDHE-ECDSA-AES128-SHA:ECDHE-RSA-AES256-SHA384:ECDHE-ECDSA-AES256-SHA384:ECDHE-RSA-AES256-SHA:ECDHE-ECDSA-AES256-SHA:DHE-RSA-AES128-SHA256:DHE-RSA-AES128-SHA:DHE-DSS-AES128-SHA256:DHE-RSA-AES256-SHA256:DHE-DSS-AES256-SHA:DHE-RSA-AES256-SHA:!aNULL:!eNULL:!EXPORT:!DES:!RC4:!3DES:!MD5:!PSK";
		if(SSL_CTX_set_cipher_list(ctx->native_handle(), ciphers.c_str())
			!= 1) {
			std::cout << "Error setting cipher list" << std::endl;
		}
	} catch(std::exception& e) {
		std::cout << "Exception: " << e.what() << std::endl;
	}
	return ctx;
}

void WebsocketServer::run(int port) {
	endpoint.listen(port);
	endpoint.start_accept();
	endpoint.run();
}

void WebsocketServer::triggerConnectionCheck() {
	for(auto it : server_connections) {
		std::string ip = ipFromHdl(it);
		endpoint.send(
			it, "{\"flag\":\"ping\"}", websocketpp::frame::opcode::text);
		check.resetTime(ip, timeSinceEpochMilliseconds());
	}
}

void WebsocketServer::stop() {
	endpoint.stop_listening();
	websocketpp::lib::error_code ec;

	for(auto it : connections) {
		endpoint.close(it, websocketpp::close::status::going_away, "", ec);

		if(ec) {
			std::cout << "Error closing connection " << ec.message()
					  << std::endl;
		}
	}
}