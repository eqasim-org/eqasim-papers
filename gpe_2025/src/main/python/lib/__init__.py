import os

def to_absolute(in_path, ref_path):
    if in_path is None:
        return None
    if os.path.isabs(in_path):
        return in_path
    return os.path.normpath(os.path.join(ref_path,in_path))

class JavaConfig:
    def __init__(self, config_dict):
        self.java_cmd = config_dict["java_cmd"]
        self.mvn_cmd = config_dict["mvn_cmd"]

class ResourcesConfig:
    def __init__(self, config_dict, max_cores):
        self.max_threads = min(max_cores, int(config_dict["max-threads"]))
        self.memory = config_dict["memory"]
        assert isinstance(self.memory, str) and ( self.memory.endswith("G") or self.memory.endswith("M"))


class FeedersConfig:
    def __init__(self, config_dict):
        for l in [config_dict["radiis"], config_dict["speeds"], config_dict["frequencies"]]:
            assert isinstance(l, list) and len(l) > 0

        self.radiis = [int(r) for r in config_dict["radiis"]]
        self.speeds = [float(s) for s in config_dict["speeds"]]
        self.frequencies = [int(r) for r in config_dict["frequencies"]]

        self.no_feeder_counterpart = None
        if "no_feeder_counterpart" in config_dict:
            self.no_feeder_counterpart = config_dict["no_feeder_counterpart"]

class ScenarioConfig:
    def __init__(self, scenario_name, config_dict, basedir):
        self.scenario_name = scenario_name
        self.path = to_absolute(config_dict["path"], basedir)
        self.prefix = config_dict["prefix"]
        self.feeders = None
        if "feeders" in config_dict:
            self.feeders = FeedersConfig(config_dict["feeders"])

class AnalysisConfig:
    def __init__(self, analysis_name, config_dict, scenarios):
        self.analysis_name = analysis_name
        self.type = config_dict["type"]
        self.scenarios = dict()
        for s, dict_s in config_dict["scenarios"].items():
            if not s in scenarios:
                raise Exception("Scenario %s mentioned by analysis %s not found" % (s, analysis_name))
            scenario_config = scenarios[s]
            feeder_config = None
            if dict_s is not None:
                feeders_config = scenario_config.feeders
                assert dict_s["feeder_setting"]["radius"] in feeders_config.radiis
                assert dict_s["feeder_setting"]["speed"] in feeders_config.speeds
                assert dict_s["feeder_setting"]["frequency"] in feeders_config.frequencies
            else:
                assert scenario_config.feeders is None
            self.scenarios[s] = feeder_config


class PipelineConfig:
    def __init__(self, config_dict, basedir, max_cores):
        self.output_path = to_absolute(config_dict["output_path"], basedir)
        self.java = JavaConfig(config_dict["java"])
        self.resources = ResourcesConfig(config_dict["resources"], max_cores)
        self.scenarios = dict()
        for s in config_dict["scenarios"]:
            self.scenarios[s] = ScenarioConfig(s, config_dict["scenarios"][s], basedir)

        for s in self.scenarios.values():
            if s.feeders is not None and (counterpart := s.feeders.no_feeder_counterpart) is not None:
                assert counterpart in self.scenarios and self.scenarios[counterpart].feeders is None

        self.analyses = dict()
        if "analyses" in config_dict:
            for a in config_dict["analyses"]:
                self.analyses[a] = AnalysisConfig(a, config_dict["analyses"][a], self.scenarios)
