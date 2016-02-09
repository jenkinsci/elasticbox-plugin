package com.elasticbox.jenkins;

import com.elasticbox.jenkins.model.instance.Instance;
import com.elasticbox.jenkins.model.repository.api.factory.instance.InstanceFactoryImpl;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by serna on 12/3/15.
 */
public class UnitTestingUtils {

    private static String twoBindingVariables = "[\n" +
            "  {\n" +
            "    \"name\": \"BINDING\",\n" +
            "    \"value\": \"com.elasticbox.jenkins.builders.DeployBox-8e11b523-9abe-4902-84d3-1309da145f65\",\n" +
            "    \"scope\": \"nested\",\n" +
            "    \"type\": \"Binding\"\n" +
            "  },\n" +
            "  {\n" +
            "    \"name\": \"ANY_BINDING\",\n" +
            "    \"value\": \"com.elasticbox.jenkins.builders.DeployBox-adf45857-30fe-4c91-af89-98cfeaff9317\",\n" +
            "    \"scope\": \"nested.nested\",\n" +
            "    \"type\": \"Binding\"\n" +
            "  }\n" +
            "]";

    private static String oneVariableForEachType = "[\n" +
            "  {\n" +
            "    \"name\": \"ANY_BINDING\",\n" +
            "    \"value\": \"com.elasticbox.jenkins.builders.DeployBox-8e11b523-9abe-4902-84d3-1309da145f65\",\n" +
            "    \"scope\": \"\",\n" +
            "    \"type\": \"Binding\"\n" +
            "  },\n" +
            "  {\n" +
            "    \"name\": \"HTTP\",\n" +
            "    \"value\": \"8080\",\n" +
            "    \"scope\": \"\",\n" +
            "    \"type\": \"Port\"\n" +
            "  },\n" +
            "  {\n" +
            "    \"name\": \"VAR_WHOLE\",\n" +
            "    \"value\": \"${TEST_TAG}\",\n" +
            "    \"scope\": \"\",\n" +
            "    \"type\": \"Text\"\n" +
            "  },\n" +
            "  {\n" +
            "    \"name\": \"VAR_INSIDE\",\n" +
            "    \"value\": \"${TEST_TAG}\",\n" +
            "    \"scope\": \"\",\n" +
            "    \"type\": \"Text\"\n" +
            "  },\n" +
            "  {\n" +
            "    \"name\": \"INSTALL_EXIT_CODE\",\n" +
            "    \"value\": \"1\",\n" +
            "    \"scope\": \"\",\n" +
            "    \"type\": \"Number\"\n" +
            "  }\n" +
            "]";

    private static String processingInstance3 = "{\n" +
            "    \"box\": \"3fbca6e6-7371-4628-861e-677c28954d2d\",\n" +
            "    \"policy_box\": {\n" +
            "      \"profile\": {\n" +
            "        \"image\": \"test\",\n" +
            "        \"instances\": 1,\n" +
            "        \"keypair\": \"test_keypair\",\n" +
            "        \"location\": \"Simulated Location\",\n" +
            "        \"flavor\": \"test.micro\",\n" +
            "        \"schema\": \"http://elasticbox.net/schemas/test/compute/profile\"\n" +
            "      },\n" +
            "      \"provider_id\": \"0476718d-2b00-45ce-8a1c-30b10a16cfc7\",\n" +
            "      \"automatic_updates\": \"off\",\n" +
            "      \"name\": \"TestDeploymentPolicy\",\n" +
            "      \"created\": \"2016-01-07 12:26:33.685146\",\n" +
            "      \"deleted\": null,\n" +
            "      \"variables\": [],\n" +
            "      \"updated\": \"2016-01-07 12:26:33.685146\",\n" +
            "      \"lifespan\": {\n" +
            "        \"operation\": \"none\"\n" +
            "      },\n" +
            "      \"visibility\": \"workspace\",\n" +
            "      \"members\": [],\n" +
            "      \"claims\": [\n" +
            "        \"test\"\n" +
            "      ],\n" +
            "      \"owner\": \"operations\",\n" +
            "      \"organization\": \"elasticbox\",\n" +
            "      \"id\": \"1763dddd-2668-4959-8907-3c84b5e98b0e\",\n" +
            "      \"schema\": \"http://elasticbox.net/schemas/boxes/policy\"\n" +
            "    },\n" +
            "    \"updated\": \"2016-01-11 11:31:22.489472\",\n" +
            "    \"automatic_updates\": \"off\",\n" +
            "    \"name\": \"scriptbox3\",\n" +
            "    \"service\": {\n" +
            "      \"type\": \"Linux Compute\",\n" +
            "      \"id\": \"eb-ipe9k\",\n" +
            "      \"machines\": []\n" +
            "    },\n" +
            "    \"tags\": [\n" +
            "      \"deploy2\"\n" +
            "    ],\n" +
            "    \"deleted\": null,\n" +
            "    \"variables\": [],\n" +
            "    \"created\": \"2016-01-11 11:31:22.489472\",\n" +
            "    \"state\": \"processing\",\n" +
            "    \"uri\": \"/services/instances/i-bcf8va\",\n" +
            "    \"application\": {\n" +
            "      \"id\": \"f616cc35-9d33-47d8-8ebd-7c8acebcd906\",\n" +
            "      \"name\": \"ApplicationBoxTestDeployName\"\n" +
            "    },\n" +
            "    \"boxes\": [\n" +
            "      {\n" +
            "        \"updated\": \"2016-01-07 12:22:58.596293\",\n" +
            "        \"automatic_updates\": \"off\",\n" +
            "        \"requirements\": [\n" +
            "          \"test\"\n" +
            "        ],\n" +
            "        \"name\": \"scriptbox3\",\n" +
            "        \"created\": \"2016-01-07 12:22:58.596293\",\n" +
            "        \"deleted\": null,\n" +
            "        \"variables\": [],\n" +
            "        \"visibility\": \"workspace\",\n" +
            "        \"events\": {},\n" +
            "        \"members\": [],\n" +
            "        \"owner\": \"operations\",\n" +
            "        \"organization\": \"elasticbox\",\n" +
            "        \"id\": \"3fbca6e6-7371-4628-861e-677c28954d2d\",\n" +
            "        \"schema\": \"http://elasticbox.net/schemas/boxes/script\"\n" +
            "      }\n" +
            "    ],\n" +
            "    \"members\": [],\n" +
            "    \"bindings\": [],\n" +
            "    \"owner\": \"operations\",\n" +
            "    \"operation\": {\n" +
            "      \"event\": \"deploy\",\n" +
            "      \"workspace\": \"operations\",\n" +
            "      \"created\": \"2016-01-11 11:31:22.486439\"\n" +
            "    },\n" +
            "    \"id\": \"i-bcf8va\",\n" +
            "    \"schema\": \"http://elasticbox.net/schemas/instance\"\n" +
            "  }";

    private static String processingInstance2 = "{\n" +
            "    \"box\": \"7ba69b26-3c8d-4ebd-9578-1982d3a8b293\",\n" +
            "    \"policy_box\": {\n" +
            "      \"profile\": {\n" +
            "        \"image\": \"test\",\n" +
            "        \"instances\": 1,\n" +
            "        \"keypair\": \"test_keypair\",\n" +
            "        \"location\": \"Simulated Location\",\n" +
            "        \"flavor\": \"test.micro\",\n" +
            "        \"schema\": \"http://elasticbox.net/schemas/test/compute/profile\"\n" +
            "      },\n" +
            "      \"provider_id\": \"0476718d-2b00-45ce-8a1c-30b10a16cfc7\",\n" +
            "      \"automatic_updates\": \"off\",\n" +
            "      \"name\": \"TestDeploymentPolicy\",\n" +
            "      \"created\": \"2016-01-07 12:26:33.685146\",\n" +
            "      \"deleted\": null,\n" +
            "      \"variables\": [],\n" +
            "      \"updated\": \"2016-01-07 12:26:33.685146\",\n" +
            "      \"lifespan\": {\n" +
            "        \"operation\": \"none\"\n" +
            "      },\n" +
            "      \"visibility\": \"workspace\",\n" +
            "      \"members\": [],\n" +
            "      \"claims\": [\n" +
            "        \"test\"\n" +
            "      ],\n" +
            "      \"owner\": \"operations\",\n" +
            "      \"organization\": \"elasticbox\",\n" +
            "      \"id\": \"1763dddd-2668-4959-8907-3c84b5e98b0e\",\n" +
            "      \"schema\": \"http://elasticbox.net/schemas/boxes/policy\"\n" +
            "    },\n" +
            "    \"updated\": \"2016-01-11 11:31:22.384439\",\n" +
            "    \"automatic_updates\": \"off\",\n" +
            "    \"name\": \"scriptbox2\",\n" +
            "    \"service\": {\n" +
            "      \"type\": \"Linux Compute\",\n" +
            "      \"id\": \"eb-huyzi\",\n" +
            "      \"machines\": []\n" +
            "    },\n" +
            "    \"tags\": [\n" +
            "      \"deploy2\"\n" +
            "    ],\n" +
            "    \"deleted\": null,\n" +
            "    \"variables\": [\n" +
            "      {\n" +
            "        \"required\": true,\n" +
            "        \"type\": \"Text\",\n" +
            "        \"name\": \"var1\",\n" +
            "        \"value\": \"value for var 1 in sb2\",\n" +
            "        \"visibility\": \"public\"\n" +
            "      },\n" +
            "      {\n" +
            "        \"name\": \"binding\",\n" +
            "        \"tags\": [\n" +
            "          \"sb1\",\n" +
            "          \"deploy2\"\n" +
            "        ],\n" +
            "        \"required\": false,\n" +
            "        \"visibility\": \"private\",\n" +
            "        \"type\": \"Binding\"\n" +
            "      }\n" +
            "    ],\n" +
            "    \"created\": \"2016-01-11 11:31:22.384439\",\n" +
            "    \"state\": \"processing\",\n" +
            "    \"uri\": \"/services/instances/i-c47q2a\",\n" +
            "    \"application\": {\n" +
            "      \"id\": \"f616cc35-9d33-47d8-8ebd-7c8acebcd906\",\n" +
            "      \"name\": \"ApplicationBoxTestDeployName\"\n" +
            "    },\n" +
            "    \"boxes\": [\n" +
            "      {\n" +
            "        \"updated\": \"2016-01-07 12:06:09.184249\",\n" +
            "        \"automatic_updates\": \"off\",\n" +
            "        \"requirements\": [\n" +
            "          \"test\",\n" +
            "          \"local\"\n" +
            "        ],\n" +
            "        \"name\": \"scriptbox2\",\n" +
            "        \"created\": \"2016-01-07 12:05:06.097307\",\n" +
            "        \"deleted\": null,\n" +
            "        \"variables\": [\n" +
            "          {\n" +
            "            \"required\": true,\n" +
            "            \"type\": \"Text\",\n" +
            "            \"name\": \"var1\",\n" +
            "            \"value\": \"\",\n" +
            "            \"visibility\": \"public\"\n" +
            "          },\n" +
            "          {\n" +
            "            \"required\": false,\n" +
            "            \"type\": \"Binding\",\n" +
            "            \"name\": \"binding\",\n" +
            "            \"value\": \"388d5e7c-2e26-490f-adcf-37cf244ee27f\",\n" +
            "            \"visibility\": \"private\"\n" +
            "          }\n" +
            "        ],\n" +
            "        \"visibility\": \"workspace\",\n" +
            "        \"events\": {},\n" +
            "        \"members\": [],\n" +
            "        \"owner\": \"operations\",\n" +
            "        \"organization\": \"elasticbox\",\n" +
            "        \"id\": \"7ba69b26-3c8d-4ebd-9578-1982d3a8b293\",\n" +
            "        \"schema\": \"http://elasticbox.net/schemas/boxes/script\"\n" +
            "      }\n" +
            "    ],\n" +
            "    \"members\": [],\n" +
            "    \"bindings\": [],\n" +
            "    \"owner\": \"operations\",\n" +
            "    \"operation\": {\n" +
            "      \"event\": \"deploy\",\n" +
            "      \"workspace\": \"operations\",\n" +
            "      \"created\": \"2016-01-11 11:31:22.371869\"\n" +
            "    },\n" +
            "    \"id\": \"i-c47q2a\",\n" +
            "    \"schema\": \"http://elasticbox.net/schemas/instance\"\n" +
            "  }";

    private static String processingInstance1 = "{\n" +
            "    \"box\": \"388d5e7c-2e26-490f-adcf-37cf244ee27f\",\n" +
            "    \"policy_box\": {\n" +
            "      \"profile\": {\n" +
            "        \"image\": \"test\",\n" +
            "        \"instances\": 1,\n" +
            "        \"keypair\": \"test_keypair\",\n" +
            "        \"location\": \"Simulated Location\",\n" +
            "        \"flavor\": \"test.micro\",\n" +
            "        \"schema\": \"http://elasticbox.net/schemas/test/compute/profile\"\n" +
            "      },\n" +
            "      \"provider_id\": \"0476718d-2b00-45ce-8a1c-30b10a16cfc7\",\n" +
            "      \"automatic_updates\": \"off\",\n" +
            "      \"name\": \"TestDeploymentPolicy\",\n" +
            "      \"created\": \"2016-01-07 12:26:33.685146\",\n" +
            "      \"deleted\": null,\n" +
            "      \"variables\": [],\n" +
            "      \"updated\": \"2016-01-07 12:26:33.685146\",\n" +
            "      \"lifespan\": {\n" +
            "        \"operation\": \"none\"\n" +
            "      },\n" +
            "      \"visibility\": \"workspace\",\n" +
            "      \"members\": [],\n" +
            "      \"claims\": [\n" +
            "        \"test\"\n" +
            "      ],\n" +
            "      \"owner\": \"operations\",\n" +
            "      \"organization\": \"elasticbox\",\n" +
            "      \"id\": \"1763dddd-2668-4959-8907-3c84b5e98b0e\",\n" +
            "      \"schema\": \"http://elasticbox.net/schemas/boxes/policy\"\n" +
            "    },\n" +
            "    \"updated\": \"2016-01-11 11:31:22.270675\",\n" +
            "    \"automatic_updates\": \"off\",\n" +
            "    \"name\": \"scriptbox1\",\n" +
            "    \"service\": {\n" +
            "      \"type\": \"Linux Compute\",\n" +
            "      \"id\": \"eb-w02mw\",\n" +
            "      \"machines\": []\n" +
            "    },\n" +
            "    \"tags\": [\n" +
            "      \"sb1\",\n" +
            "      \"deploy2\"\n" +
            "    ],\n" +
            "    \"deleted\": null,\n" +
            "    \"variables\": [\n" +
            "      {\n" +
            "        \"required\": true,\n" +
            "        \"type\": \"Text\",\n" +
            "        \"name\": \"var1\",\n" +
            "        \"value\": \"value for var1 in sb1\",\n" +
            "        \"visibility\": \"public\"\n" +
            "      }\n" +
            "    ],\n" +
            "    \"created\": \"2016-01-11 11:31:22.270675\",\n" +
            "    \"state\": \"processing\",\n" +
            "    \"uri\": \"/services/instances/i-kbfgmo\",\n" +
            "    \"application\": {\n" +
            "      \"id\": \"f616cc35-9d33-47d8-8ebd-7c8acebcd906\",\n" +
            "      \"name\": \"ApplicationBoxTestDeployName\"\n" +
            "    },\n" +
            "    \"boxes\": [\n" +
            "      {\n" +
            "        \"updated\": \"2016-01-07 12:04:30.407625\",\n" +
            "        \"automatic_updates\": \"off\",\n" +
            "        \"requirements\": [\n" +
            "          \"test\",\n" +
            "          \"linux\"\n" +
            "        ],\n" +
            "        \"name\": \"scriptbox1\",\n" +
            "        \"created\": \"2016-01-07 12:03:37.413823\",\n" +
            "        \"deleted\": null,\n" +
            "        \"variables\": [\n" +
            "          {\n" +
            "            \"required\": true,\n" +
            "            \"type\": \"Text\",\n" +
            "            \"name\": \"var1\",\n" +
            "            \"value\": \"\",\n" +
            "            \"visibility\": \"public\"\n" +
            "          }\n" +
            "        ],\n" +
            "        \"visibility\": \"workspace\",\n" +
            "        \"id\": \"388d5e7c-2e26-490f-adcf-37cf244ee27f\",\n" +
            "        \"members\": [],\n" +
            "        \"owner\": \"operations\",\n" +
            "        \"organization\": \"elasticbox\",\n" +
            "        \"events\": {},\n" +
            "        \"schema\": \"http://elasticbox.net/schemas/boxes/script\"\n" +
            "      }\n" +
            "    ],\n" +
            "    \"members\": [],\n" +
            "    \"bindings\": [],\n" +
            "    \"owner\": \"operations\",\n" +
            "    \"operation\": {\n" +
            "      \"event\": \"deploy\",\n" +
            "      \"workspace\": \"operations\",\n" +
            "      \"created\": \"2016-01-11 11:31:22.268407\"\n" +
            "    },\n" +
            "    \"id\": \"i-kbfgmo\",\n" +
            "    \"schema\": \"http://elasticbox.net/schemas/instance\"\n" +
            "  }";


    private static String deployScriptBoxRequest = "{\n" +
            "  \"schema\": \"http://elasticbox.net/schemas/deploy-instance-request\",\n" +
            "  \"owner\": \"operations\",\n" +
            "  \"name\": \"RabbitMQ\",\n" +
            "  \"box\": {\n" +
            "    \"id\": \"5097988a-895c-4a7b-bf3a-2871a9144a20\",\n" +
            "    \"variables\": [\n" +
            "      {\n" +
            "        \"name\": \"username\",\n" +
            "        \"type\": \"Text\",\n" +
            "        \"value\": \"username_value\",\n" +
            "        \"required\": false,\n" +
            "        \"visibility\": \"public\"\n" +
            "      },\n" +
            "      {\n" +
            "        \"name\": \"password\",\n" +
            "        \"type\": \"Password\",\n" +
            "        \"value\": \"password_value\",\n" +
            "        \"required\": false,\n" +
            "        \"visibility\": \"public\"\n" +
            "      }\n" +
            "    ]\n" +
            "  },\n" +
            "  \"instance_tags\": [\n" +
            "    \"deployment_tag\"\n" +
            "  ],\n" +
            "  \"automatic_updates\": \"off\",\n" +
            "  \"policy_box\": {\n" +
            "    \"id\": \"1763dddd-2668-4959-8907-3c84b5e98b0e\",\n" +
            "    \"variables\": [\n" +
            "      {\n" +
            "        \"name\": \"policy_box_variable\",\n" +
            "        \"type\": \"Text\",\n" +
            "        \"value\": \"value_2\",\n" +
            "        \"required\": false,\n" +
            "        \"visibility\": \"public\"\n" +
            "      }\n" +
            "    ]\n" +
            "  }\n" +
            "}";

    private static String applicationBox = "{\n" +
            "  \"schema\": \"http://elasticbox.net/schemas/boxes/application\",\n" +
            "  \"updated\": \"2016-01-07 12:38:42.689994\",\n" +
            "  \"description\": \"just to test that exists\",\n" +
            "  \"created\": \"2015-12-03 13:53:47.562301\",\n" +
            "  \"deleted\": null,\n" +
            "  \"variables\": [],\n" +
            "  \"uri\": \"/services/boxes/f616cc35-9d33-47d8-8ebd-7c8acebcd906\",\n" +
            "  \"visibility\": \"workspace\",\n" +
            "  \"services\": [\n" +
            "    {\n" +
            "      \"box\": {\n" +
            "        \"variables\": [\n" +
            "          {\n" +
            "            \"name\": \"var1\",\n" +
            "            \"value\": \"value for var1 in sb1\"\n" +
            "          }\n" +
            "        ],\n" +
            "        \"id\": \"388d5e7c-2e26-490f-adcf-37cf244ee27f\",\n" +
            "        \"latest\": true\n" +
            "      },\n" +
            "      \"policy\": {\n" +
            "        \"variables\": [],\n" +
            "        \"requirements\": [\n" +
            "          \"test\"\n" +
            "        ]\n" +
            "      },\n" +
            "      \"automatic_updates\": \"off\",\n" +
            "      \"name\": \"scriptbox1\",\n" +
            "      \"tags\": [\n" +
            "        \"sb1\"\n" +
            "      ]\n" +
            "    },\n" +
            "    {\n" +
            "      \"box\": {\n" +
            "        \"variables\": [\n" +
            "          {\n" +
            "            \"name\": \"var1\",\n" +
            "            \"value\": \"value for var 1 in sb2\"\n" +
            "          },\n" +
            "          {\n" +
            "            \"name\": \"binding\",\n" +
            "            \"value\": \"sb1\"\n" +
            "          }\n" +
            "        ],\n" +
            "        \"id\": \"7ba69b26-3c8d-4ebd-9578-1982d3a8b293\",\n" +
            "        \"latest\": true\n" +
            "      },\n" +
            "      \"policy\": {\n" +
            "        \"variables\": [],\n" +
            "        \"requirements\": [\n" +
            "          \"test\"\n" +
            "        ]\n" +
            "      },\n" +
            "      \"automatic_updates\": \"off\",\n" +
            "      \"name\": \"scriptbox2\",\n" +
            "      \"tags\": []\n" +
            "    },\n" +
            "    {\n" +
            "      \"box\": {\n" +
            "        \"variables\": [],\n" +
            "        \"id\": \"3fbca6e6-7371-4628-861e-677c28954d2d\",\n" +
            "        \"latest\": true\n" +
            "      },\n" +
            "      \"policy\": {\n" +
            "        \"variables\": [],\n" +
            "        \"requirements\": [\n" +
            "          \"test\"\n" +
            "        ]\n" +
            "      },\n" +
            "      \"automatic_updates\": \"off\",\n" +
            "      \"name\": \"scriptbox3\",\n" +
            "      \"tags\": []\n" +
            "    }\n" +
            "  ],\n" +
            "  \"members\": [],\n" +
            "  \"owner\": \"operations\",\n" +
            "  \"organization\": \"elasticbox\",\n" +
            "  \"id\": \"f616cc35-9d33-47d8-8ebd-7c8acebcd906\",\n" +
            "  \"name\": \"ApplicationBoxTest\"\n" +
            "}";

    private static String emptyApplicationBox = "{\n" +
            "\"updated\": \"2015-12-03 13:53:47.562301\",\n" +
            "\"description\": \"just to test that exists\",\n" +
            "\"created\": \"2015-12-03 13:53:47.562301\",\n" +
            "\"deleted\": null,\n" +
            "\"variables\": [],\n" +
            "\"uri\": \"/services/boxes/f616cc35-9d33-47d8-8ebd-7c8acebcd906\",\n" +
            "\"visibility\": \"workspace\",\n" +
            "\"name\": \"ApplicationBoxTest\",\n" +
            "\"owner\": \"operations\",\n" +
            "\"members\": [],\n" +
            "\"services\": [],\n" +
            "\"organization\": \"elasticbox\",\n" +
            "\"id\": \"f616cc35-9d33-47d8-8ebd-7c8acebcd906\",\n" +
            "\"schema\": \"http://elasticbox.net/schemas/boxes/application\"\n" +
            "}";

    private static  String  scriptBox = "{\n" +
            "\"updated\": \"2015-11-17 16:47:06.005841\",\n" +
            "\"automatic_updates\": \"off\",\n" +
            "\"requirements\": [\n" +
            "\"req1\",\n" +
            "\"req2\"\n" +
            "],\n" +
            "\"description\": \"desc of the box\",\n" +
            "\"name\": \"PruebaS3\",\n" +
            "\"created\": \"2015-11-05 23:47:02.643547\",\n" +
            "\"deleted\": null,\n" +
            "\"variables\": [],\n" +
            "\"uri\": \"/services/boxes/f3ef667a-2d3b-4846-af75-7d7996505a92\",\n" +
            "\"visibility\": \"workspace\",\n" +
            "\"id\": \"f3ef667a-2d3b-4846-af75-7d7996505a92\",\n" +
            "\"members\": [],\n" +
            "\"owner\": \"operations\",\n" +
            "\"organization\": \"elasticbox\",\n" +
            "\"events\": {},\n" +
            "\"schema\": \"http://elasticbox.net/schemas/boxes/script\"\n" +
            "}";

    private static String cloudFormationTemplatePolicyBox = "{\n" +
            "\"profile\": {\n" +
            "\"location\": \"ap-northeast-1\",\n" +
            "\"schema\": \"http://elasticbox.net/schemas/aws/cloudformation/profile\"\n" +
            "},\n" +
            "\"schema\": \"http://elasticbox.net/schemas/boxes/policy\",\n" +
            "\"provider_id\": \"77bb43a7-7122-44ba-aa6f-6f0886eccabd\",\n" +
            "\"automatic_updates\": \"off\",\n" +
            "\"name\": \"PolicyCF\",\n" +
            "\"created\": \"2015-11-30 10:46:45.554591\",\n" +
            "\"deleted\": null,\n" +
            "\"variables\": [],\n" +
            "\"updated\": \"2015-11-30 10:46:45.554591\",\n" +
            "\"lifespan\": {\n" +
            "\"operation\": \"none\"\n" +
            "},\n" +
            "\"uri\": \"/services/boxes/fa7472a4-c284-43c1-9b96-ada6965d365f\",\n" +
            "\"visibility\": \"workspace\",\n" +
            "\"members\": [],\n" +
            "\"claims\": [],\n" +
            "\"owner\": \"operations\",\n" +
            "\"organization\": \"elasticbox\",\n" +
            "\"id\": \"fa7472a4-c284-43c1-9b96-ada6965d365f\",\n" +
            "\"description\": \"Una policy para desplegar CF Template boxes\"\n" +
            "}";

    private static String policyBox = "{\n" +
            "\"profile\": {\n" +
            "\"subnet\": \"us-east-1a\",\n" +
            "\"cloud\": \"EC2\",\n" +
            "\"image\": \"Linux Compute\",\n" +
            "\"instances\": 1,\n" +
            "\"keypair\": \"None\",\n" +
            "\"location\": \"us-east-1\",\n" +
            "\"volumes\": [],\n" +
            "\"flavor\": \"m1.large\",\n" +
            "\"security_groups\": [\n" +
            "\"Automatic\"\n" +
            "],\n" +
            "\"schema\": \"http://elasticbox.net/schemas/aws/ec2/profile\"\n" +
            "},\n" +
            "\"provider_id\": \"77bb43a7-7122-44ba-aa6f-6f0886eccabd\",\n" +
            "\"automatic_updates\": \"off\",\n" +
            "\"name\": \"default-large-us-east-1\",\n" +
            "\"created\": \"2015-11-05 17:53:55.266635\",\n" +
            "\"deleted\": null,\n" +
            "\"variables\": [],\n" +
            "\"updated\": \"2015-11-17 16:47:06.000420\",\n" +
            "\"visibility\": \"workspace\",\n" +
            "\"uri\": \"/services/boxes/0308884a-d373-4e37-9e4f-70c1645cad0b\",\n" +
            "\"owner\": \"operations\",\n" +
            "\"members\": [],\n" +
            "\"organization\": \"elasticbox\",\n" +
            "\"readme\": {\n" +
            "\"url\": \"/resources/default_box_overview.md\",\n" +
            "\"upload_date\": \"2015-11-05 17:53:55.265901\",\n" +
            "\"length\": 1302,\n" +
            "\"content_type\": \"text/x-markdown\"\n" +
            "},\n" +
            "\"claims\": [\n" +
            "\"large\",\n" +
            "\"linux\"\n" +
            "],\n" +
            "\"id\": \"0308884a-d373-4e37-9e4f-70c1645cad0b\",\n" +
            "\"schema\": \"http://elasticbox.net/schemas/boxes/policy\"\n" +
            "}";

    private static String templateCloudFormationBox = "{\n" +
            "\"updated\": \"2015-11-26 10:11:54.669276\",\n" +
            "\"automatic_updates\": \"off\",\n" +
            "\"requirements\": [],\n" +
            "\"name\": \"CF Template\",\n" +
            "\"created\": \"2015-11-25 16:40:12.054144\",\n" +
            "\"deleted\": null,\n" +
            "\"type\": \"CloudFormation Service\",\n" +
            "\"variables\": [\n" +
            "{\n" +
            "\"required\": false,\n" +
            "\"type\": \"Text\",\n" +
            "\"name\": \"KeyName\",\n" +
            "\"value\": \"\",\n" +
            "\"visibility\": \"public\"\n" +
            "},\n" +
            "{\n" +
            "\"required\": false,\n" +
            "\"type\": \"Text\",\n" +
            "\"name\": \"InstanceType\",\n" +
            "\"value\": \"m1.small\",\n" +
            "\"visibility\": \"public\"\n" +
            "},\n" +
            "{\n" +
            "\"visibility\": \"public\",\n" +
            "\"type\": \"Text\",\n" +
            "\"name\": \"DBName\",\n" +
            "\"value\": \"wordpressdb\"\n" +
            "},\n" +
            "{\n" +
            "\"visibility\": \"public\",\n" +
            "\"type\": \"Text\",\n" +
            "\"name\": \"SSHLocation\",\n" +
            "\"value\": \"0.0.0.0/0\"\n" +
            "},\n" +
            "{\n" +
            "\"visibility\": \"public\",\n" +
            "\"type\": \"Text\",\n" +
            "\"name\": \"DBPassword\",\n" +
            "\"value\": \"\"\n" +
            "},\n" +
            "{\n" +
            "\"visibility\": \"public\",\n" +
            "\"type\": \"Text\",\n" +
            "\"name\": \"DBUser\",\n" +
            "\"value\": \"\"\n" +
            "},\n" +
            "{\n" +
            "\"visibility\": \"public\",\n" +
            "\"type\": \"Text\",\n" +
            "\"name\": \"DBRootPassword\",\n" +
            "\"value\": \"\"\n" +
            "}\n" +
            "],\n" +
            "\"description\": \"Tiene policy\",\n" +
            "\"uri\": \"/services/boxes/3d87d385-8710-47c3-951e-7112d8db25f4\",\n" +
            "\"visibility\": \"workspace\",\n" +
            "\"members\": [],\n" +
            "\"owner\": \"operations\",\n" +
            "\"organization\": \"elasticbox\",\n" +
            "\"template\": {\n" +
            "\"url\": \"/services/blobs/download/5656daea14841238d2f083a1/template.json\",\n" +
            "\"upload_date\": \"2015-11-26 10:11:54.628201\",\n" +
            "\"length\": 15489,\n" +
            "\"content_type\": \"text/x-shellscript\"\n" +
            "},\n" +
            "\"id\": \"3d87d385-8710-47c3-951e-7112d8db25f4\",\n" +
            "\"schema\": \"http://elasticbox.net/schemas/boxes/cloudformation\"\n" +
            "}";

    private static String managedCloudFormationBox = "{\n" +
            "\"profile\": {\n" +
            "\"range\": {\n" +
            "\"type\": \"none\",\n" +
            "\"name\": \"\"\n" +
            "},\n" +
            "\"capacity\": {\n" +
            "\"read\": 5,\n" +
            "\"write\": 5\n" +
            "},\n" +
            "\"location\": \"ap-northeast-1\",\n" +
            "\"key\": {\n" +
            "\"type\": \"str\",\n" +
            "\"name\": \"Key Name\"\n" +
            "},\n" +
            "\"schema\": \"http://elasticbox.net/schemas/aws/ddb/profile\"\n" +
            "},\n" +
            "\"schema\": \"http://elasticbox.net/schemas/boxes/cloudformation\",\n" +
            "\"provider_id\": \"77bb43a7-7122-44ba-aa6f-6f0886eccabd\",\n" +
            "\"automatic_updates\": \"off\",\n" +
            "\"requirements\": [],\n" +
            "\"name\": \"CF Managed\",\n" +
            "\"created\": \"2015-11-25 16:39:14.925122\",\n" +
            "\"deleted\": null,\n" +
            "\"variables\": [\n" +
            "{\n" +
            "\"visibility\": \"public\",\n" +
            "\"type\": \"Options\",\n" +
            "\"name\": \"key_type\",\n" +
            "\"value\": \"str\",\n" +
            "\"options\": \"int,long,float,str,unicode,Binary\"\n" +
            "},\n" +
            "{\n" +
            "\"type\": \"Text\",\n" +
            "\"visibility\": \"public\",\n" +
            "\"value\": \"str\",\n" +
            "\"name\": \"key_name\"\n" +
            "},\n" +
            "{\n" +
            "\"type\": \"Port\",\n" +
            "\"visibility\": \"public\",\n" +
            "\"value\": \"80\",\n" +
            "\"name\": \"port\"\n" +
            "},\n" +
            "{\n" +
            "\"type\": \"Text\",\n" +
            "\"visibility\": \"public\",\n" +
            "\"value\": \"\",\n" +
            "\"name\": \"range_name\"\n" +
            "},\n" +
            "{\n" +
            "\"visibility\": \"public\",\n" +
            "\"type\": \"Options\",\n" +
            "\"name\": \"range_type\",\n" +
            "\"value\": \"none\",\n" +
            "\"options\": \"none,int,long,float,str,unicode,Binary\"\n" +
            "},\n" +
            "{\n" +
            "\"type\": \"Number\",\n" +
            "\"visibility\": \"public\",\n" +
            "\"value\": \"5\",\n" +
            "\"name\": \"read_capacity_units\"\n" +
            "},\n" +
            "{\n" +
            "\"type\": \"Number\",\n" +
            "\"visibility\": \"public\",\n" +
            "\"value\": \"5\",\n" +
            "\"name\": \"write_capacity_units\"\n" +
            "}\n" +
            "],\n" +
            "\"updated\": \"2015-11-25 16:40:25.599207\",\n" +
            "\"visibility\": \"workspace\",\n" +
            "\"uri\": \"/services/boxes/02fab23c-5278-41ec-8d9e-0f7936582937\",\n" +
            "\"members\": [{\n" +
            "\"role\": \"collaborator\",\n" +
            "\"workspace\": \"jenkins1\"\n" +
            "},\n" +
            "{\n" +
            "\"role\": \"collaborator\",\n" +
            "\"workspace\": \"engineering\"\n" +
            "}],\n" +
            "\"owner\": \"operations\",\n" +
            "\"organization\": \"elasticbox\",\n" +
            "\"type\": \"Dynamo DB Domain\",\n" +
            "\"id\": \"02fab23c-5278-41ec-8d9e-0f7936582937\",\n" +
            "\"description\": \"No tiene policy\"\n" +
            "}";


    public static JSONArray getFakeProcessingInstancesArray(){
        final JSONObject instance1 = (JSONObject) JSONSerializer.toJSON(processingInstance1);
        final JSONObject instance2 = (JSONObject) JSONSerializer.toJSON(processingInstance2);
        final JSONObject instance3 = (JSONObject) JSONSerializer.toJSON(processingInstance3);
        final JSONArray jsonArray = new JSONArray();
        jsonArray.add(instance1);
        jsonArray.add(instance2);
        jsonArray.add(instance3);
        return jsonArray;
    }

    public static List<Instance> getFakeProcessingInstancesList(){
        final JSONObject instance1JSON = (JSONObject) JSONSerializer.toJSON(processingInstance1);
        final Instance instance1 = new InstanceFactoryImpl().create(instance1JSON);

        final JSONObject instance2JSON = (JSONObject) JSONSerializer.toJSON(processingInstance2);
        final Instance instance2 = new InstanceFactoryImpl().create(instance2JSON);

        final JSONObject instance3JSON = (JSONObject) JSONSerializer.toJSON(processingInstance3);
        final Instance instance3 = new InstanceFactoryImpl().create(instance3JSON);

        List<Instance> instances = new ArrayList<>();
        instances.add(instance1);
        instances.add(instance2);
        instances.add(instance3);

        return instances;
    }

    public static List<Instance> getFakeDoneInstancesList(){
        final JSONObject instance1JSON = (JSONObject) JSONSerializer.toJSON(processingInstance1);
        instance1JSON.put("state", "done");
        final Instance instance1 = new InstanceFactoryImpl().create(instance1JSON);

        final JSONObject instance2JSON = (JSONObject) JSONSerializer.toJSON(processingInstance2);
        instance2JSON.put("state", "done");
        final Instance instance2 = new InstanceFactoryImpl().create(instance2JSON);

        final JSONObject instance3JSON = (JSONObject) JSONSerializer.toJSON(processingInstance3);
        instance3JSON.put("state", "done");
        final Instance instance3 = new InstanceFactoryImpl().create(instance3JSON);

        List<Instance> instances = new ArrayList<>();
        instances.add(instance1);
        instances.add(instance2);
        instances.add(instance3);

        return instances;
    }

    public static JSONObject getFakeDoneInstance(){

        final JSONObject fromObject = JSONObject.fromObject(processingInstance1);
        fromObject.put("state", "done");
        return  fromObject;
    }

    public static JSONArray getTwoBindingVariables(){
        return (JSONArray)  JSONArray.fromObject(twoBindingVariables);
    }

    public static JSONArray getOneVariableForEachType(){
        return (JSONArray)  JSONArray.fromObject(oneVariableForEachType);
    }

    public static JSONObject getFakeProcessingInstance(){
        return (JSONObject)  JSONObject.fromObject(processingInstance1);
    }

    public static JSONObject getFakeEmptyApplicationBox(){
        return (JSONObject) JSONSerializer.toJSON(emptyApplicationBox);
    }

    public static JSONObject getFakeApplicationBox(){
        return (JSONObject) JSONSerializer.toJSON(applicationBox);
    }

    public static JSONObject getFakeScriptBox(){
        return (JSONObject) JSONSerializer.toJSON(scriptBox);
    }

    public static JSONObject getFakePolicyBox(){
        return (JSONObject) JSONSerializer.toJSON(policyBox);
    }

    public static JSONObject getFakeCloudFormationTemplatePolicyBox(){
        return (JSONObject) JSONSerializer.toJSON(cloudFormationTemplatePolicyBox);
    }

    public static JSONObject getFakeCloudFormationTemplateBox(){
        return (JSONObject) JSONSerializer.toJSON(templateCloudFormationBox);
    }

    public static JSONObject getFakeCloudFormationManagedBox(){
        return (JSONObject) JSONSerializer.toJSON(managedCloudFormationBox);
    }

    public static JSONObject [] getFakeArrayContainingOneFakeBoxForEachType(){
        return new JSONObject[]{
                getFakeScriptBox(),
                getFakePolicyBox(),
                getFakeCloudFormationTemplateBox(),
                getFakeCloudFormationManagedBox(),
                getFakeEmptyApplicationBox()
        };

    }

    public static JSONArray getFakeJSONArrayContainingOneFakeBoxForEachType(){
            final JSONArray array = new JSONArray();
            array.add(getFakeScriptBox());
            array.add(getFakePolicyBox());
            array.add(getFakeCloudFormationTemplateBox());
            array.add(getFakeCloudFormationManagedBox());
            array.add(getFakeEmptyApplicationBox());
            array.add(getFakeCloudFormationTemplatePolicyBox());
            return array;
    }

    public static JSONArray getJSONArrayFromFile(String file) throws IOException {
        InputStream is = UnitTestingUtils.class.getResourceAsStream(file);
        String jsonTxt = IOUtils.toString(is);
        return (JSONArray) JSONSerializer.toJSON(jsonTxt);
    }

    public static JSONObject getFakeScriptBoxDeployRequest(){
            return (JSONObject) JSONSerializer.toJSON(deployScriptBoxRequest);
    }



}
