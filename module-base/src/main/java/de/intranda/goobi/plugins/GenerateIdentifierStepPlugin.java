package de.intranda.goobi.plugins;

import java.io.IOException;

/**
 * This file is part of a plugin for Goobi - a Workflow tool for the support of mass digitization.
 *
 * Visit the websites for more information.
 *          - https://goobi.io
 *          - https://www.intranda.com
 *          - https://github.com/intranda/goobi
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.exceptions.SwapException;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.exceptions.WriteException;

@PluginImplementation
@Log4j2
public class GenerateIdentifierStepPlugin implements IStepPluginVersion2 {

    @Getter
    private String title = "intranda_step_generateIdentifier";
    @Getter
    private Step step;
    private String type;
    private String field;
    private int length;
    private String returnPath;
    private boolean overwrite = false;

    @Override
    public void initialize(Step step, String returnPath) {
        this.returnPath = returnPath;
        this.step = step;

        // read parameters from correct block in configuration file
        SubnodeConfiguration myconfig = ConfigPlugins.getProjectAndStepConfig(title, step);
        field = myconfig.getString("field", "CatalogIDDigital");
        type = myconfig.getString("type", "uuid");
        length = myconfig.getInt("length", 9);
        overwrite = myconfig.getBoolean("overwrite", false);
        log.info("GenerateIdentifier step plugin initialized");
    }

    @Override
    public PluginGuiType getPluginGuiType() {
        return PluginGuiType.NONE;
    }

    @Override
    public String getPagePath() {
        return "/uii/plugin_step_generateIdentifier.xhtml";
    }

    @Override
    public PluginType getType() {
        return PluginType.Step;
    }

    @Override
    public String cancel() {
        return "/uii" + returnPath;
    }

    @Override
    public String finish() {
        return "/uii" + returnPath;
    }

    @Override
    public int getInterfaceVersion() {
        return 0;
    }

    @Override
    public HashMap<String, StepReturnValue> validate() {
        return null;
    }

    @Override
    public boolean execute() {
        PluginReturnValue ret = run();
        return ret != PluginReturnValue.ERROR;
    }

    @Override
    public PluginReturnValue run() {
        boolean successfull = true;

        String myId = "";

        switch (type.toLowerCase()) {

            case "random":
                // generate a new number
                myId = String.valueOf(ThreadLocalRandom.current().nextInt(1, 999999999 + 1));
                // shorten it, if it is too long
                if (myId.length() > length) {
                    myId = myId.substring(0, length);
                }
                // fill it with zeros if it is too short
                myId = StringUtils.leftPad(myId, length, "0");
                break;

            case "timestamp":
                // timestamp
                long time = System.currentTimeMillis();
                myId = Long.toString(time);
                break;

            default:
                // uuid
                UUID uuid = UUID.randomUUID();
                myId = uuid.toString();
                break;
        }

        try {
            // read the mets file
            Fileformat ff = step.getProzess().readMetadataFile();
            DocStruct logical = ff.getDigitalDocument().getLogicalDocStruct();
            if (logical.getType().isAnchor()) {
                logical = logical.getAllChildren().get(0);
            }

            // find the correct metadata type
            MetadataType mdt = step.getProzess().getRegelsatz().getPreferences().getMetadataTypeByName(field);

            // update existing metadata of the given field if they exist
            if (mdt != null) {
                List<? extends Metadata> mdl = logical.getAllMetadataByType(mdt);
                if (mdl != null && !mdl.isEmpty()) {
                    if (overwrite) {
                        for (Metadata m : mdl) {
                            m.setValue(myId);
                        }
                    }
                } else {
                    // create new metadata
                    Metadata m = new Metadata(mdt);
                    m.setValue(myId);
                    logical.addMetadata(m);
                }
            }

            // save the mets file again
            step.getProzess().writeMetadataFile(ff);
        } catch (ReadException | PreferencesException | SwapException | WriteException | IOException | MetadataTypeNotAllowedException e) {
            log.error("Error while generating new metadata inside of GenerateIdentifier step plugin", e);
            successfull = false;
        }

        log.info("GenerateIdentifier step plugin executed");
        if (!successfull) {
            return PluginReturnValue.ERROR;
        }
        return PluginReturnValue.FINISH;
    }

}
