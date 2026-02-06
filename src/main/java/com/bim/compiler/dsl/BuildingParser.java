package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingDefinition.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for BUILDING DSL syntax.
 *
 * Uses brace-counting for nested blocks instead of complex regex.
 */
public class BuildingParser {

    // Simple patterns (no nested braces)
    private static final Pattern STAIR_PATTERN = Pattern.compile(
        "STAIR\\s+\"([^\"]+)\"\\s+at:(\\w+)\\s+width:([\\d.]+)m\\s+to:\"([^\"]+)\""
    );

    private static final Pattern LANDING_PATTERN = Pattern.compile(
        "LANDING\\s+\"([^\"]+)\"\\s+at:(\\w+)\\s+size:([\\d.]+)x([\\d.]+)m\\s+from:\"([^\"]+)\""
    );

    // Phase 56: Vertical circulation patterns for high-rise
    // ELEVATOR "lift_1" type:PASSENGER car:1100x1400 door:900
    private static final Pattern ELEVATOR_PATTERN = Pattern.compile(
        "ELEVATOR\\s+\"([^\"]+)\"\\s+type:(\\w+)\\s+car:(\\d+)x(\\d+)\\s+door:(\\d+)"
    );

    // ELEVATOR_LOBBY "lobby" bounds:C2-D4 pressurized:true fire_rating:2hr
    private static final Pattern ELEVATOR_LOBBY_PATTERN = Pattern.compile(
        "(?:ELEVATOR_LOBBY|LIFT_LOBBY)\\s+\"([^\"]+)\"\\s+bounds:([A-Za-z]\\d+-[A-Za-z]\\d+)" +
        "(?:\\s+pressurized:(true|false))?(?:\\s+fire_rating:([\\d.]+)hr)?"
    );

    // SHAFT "elec_riser" at:D1 size:1.5x1.5m type:ELECTRICAL
    private static final Pattern SHAFT_PATTERN = Pattern.compile(
        "SHAFT\\s+\"([^\"]+)\"\\s+at:(\\w+)\\s+size:([\\d.]+)x([\\d.]+)m\\s+type:(\\w+)"
    );

    // Extended STAIR with type and pressurized
    // STAIR "stair_A" at:C1 width:1.2m type:PROTECTED pressurized:true
    private static final Pattern STAIR_EXTENDED_PATTERN = Pattern.compile(
        "STAIR\\s+\"([^\"]+)\"\\s+at:(\\w+)\\s+width:([\\d.]+)m\\s+type:(\\w+)(?:\\s+pressurized:(true|false))?"
    );

    // Phase 56B: CORE block (building-level vertical circulation)
    // CORE "main_core" bounds:C1-D6 { ... }
    private static final Pattern CORE_PATTERN = Pattern.compile(
        "CORE\\s+\"([^\"]+)\"\\s+bounds:([A-Za-z]\\d+-[A-Za-z]\\d+)"
    );

    private static final Pattern DOOR_PATTERN = Pattern.compile(
        "DOOR\\s+(north|south|east|west)(?:\\s+to:(\\w+))?(?:\\s+size:(\\d+)x(\\d+))?"
    );

    private static final Pattern WINDOW_PATTERN = Pattern.compile(
        "WINDOW\\s+(north|south|east|west)(?:\\s+size:(\\d+)x(\\d+))?"
    );

    // Phase 26: Extended ROOF with optional overhang
    private static final Pattern ROOF_PATTERN = Pattern.compile(
        "ROOF\\s+pitch:(\\d+)deg(?:\\s+overhang:(\\d+)mm)?"
    );

    // Phase 26: Grid bounds pattern (bounds:A2-B4)
    private static final Pattern GRID_BOUNDS_PATTERN = Pattern.compile(
        "bounds:([A-Za-z]\\d+-[A-Za-z]\\d+)"
    );

    // Phase 26: Porch roof type (allows optional whitespace after colon)
    private static final Pattern PORCH_ROOF_PATTERN = Pattern.compile(
        "roof:\\s*(ATTACHED|SEPARATE)"
    );

    private static final Pattern SPRINKLER_PATTERN = Pattern.compile(
        "SPRINKLERS\\s+grid:([\\d.]+)m"
    );

    private static final Pattern LIGHT_PATTERN = Pattern.compile(
        "LIGHTS\\s+grid:([\\d.]+)m(?:\\s+type:(\\w+))?"
    );

    // Layer 3: Constraint patterns
    // Note: ADJACENT must NOT match inside NOT_ADJACENT, so use negative lookbehind
    private static final Pattern ADJACENT_PATTERN = Pattern.compile(
        "(?<!not_)adjacent:\\s*(\\w+)"
    );

    private static final Pattern NOT_ADJACENT_PATTERN = Pattern.compile(
        "not_adjacent:\\s*(\\w+)"
    );

    private static final Pattern EXTERIOR_PATTERN = Pattern.compile(
        "exterior:\\s*(north|south|east|west)"
    );

    // Phase 16: Vertical constraint (cross-storey alignment)
    private static final Pattern ALIGNS_PATTERN = Pattern.compile(
        "aligns:\\s*(\\w+)"
    );

    // Phase 17: Extended vertical vocabulary
    private static final Pattern ABOVE_PATTERN = Pattern.compile(
        "above:\\s*(\\w+)"
    );

    private static final Pattern BELOW_PATTERN = Pattern.compile(
        "below:\\s*(\\w+)"
    );

    private static final Pattern STACK_PATTERN = Pattern.compile(
        "stack:\\s*(\\w+)"
    );

    // Phase 28: SCHEDULE entry pattern
    // D1: 900x2100 "Metal frame solid timber"
    private static final Pattern SCHEDULE_ENTRY_PATTERN = Pattern.compile(
        "(\\w+):\\s*(\\d+)x(\\d+)(?:\\s+\"([^\"]*)\")?"
    );

    // Phase 27: TB-LKTN extensions
    // opens_to: constraint (implies door connection to open plan)
    private static final Pattern OPENS_TO_PATTERN = Pattern.compile(
        "opens_to:\\s*(\\w+)"
    );

    // zones: list for OPEN_PLAN
    private static final Pattern ZONES_PATTERN = Pattern.compile(
        "zones:\\s*([A-Z_,\\s]+)"
    );

    // Extended DOOR with type and wall: DOOR type:D1 size:900x2100 wall:south
    private static final Pattern DOOR_EXTENDED_PATTERN = Pattern.compile(
        "DOOR\\s+type:(\\w+)\\s+size:(\\d+)x(\\d+)(?:\\s+wall:(north|south|east|west))?"
    );

    // Extended WINDOW with type and wall: WINDOW type:W1 size:1800x1000 wall:west
    private static final Pattern WINDOW_EXTENDED_PATTERN = Pattern.compile(
        "WINDOW\\s+type:(\\w+)\\s+size:(\\d+)x(\\d+)(?:\\s+wall:(north|south|east|west))?"
    );

    // Phase 28: Profile/Protocol/LOD patterns
    private static final Pattern PROFILE_PATTERN = Pattern.compile(
        "profile:\\s*\"([^\"]+)\""
    );
    private static final Pattern PROTOCOL_PATTERN = Pattern.compile(
        "protocol:\\s*\"([^\"]+)\""
    );
    private static final Pattern LOD_PATTERN = Pattern.compile(
        "lod:\\s*(\\d+)"
    );

    // Phase 50B.1: Construction system pattern
    private static final Pattern CONSTRUCTION_PATTERN = Pattern.compile(
        "construction:(FRAMED|MASONRY)"
    );

    // Phase 46: Multi-unit patterns
    // type:MULTI_UNIT on BUILDING header
    private static final Pattern BUILDING_TYPE_PATTERN = Pattern.compile(
        "type:(SINGLE_UNIT|MULTI_UNIT)"
    );
    // UNIT "name" type:RESIDENTIAL entry:DIRECT {
    private static final Pattern UNIT_PATTERN = Pattern.compile(
        "UNIT\\s+\"([^\"]+)\"(?:\\s+type:(RESIDENTIAL|COMMERCIAL))?(?:\\s+entry:(DIRECT|SHARED))?\\s*\\{"
    );
    // SHARED { (standalone block, not entry:SHARED in UNIT)
    private static final Pattern SHARED_PATTERN = Pattern.compile(
        "(?<!:)\\bSHARED\\s*\\{"
    );
    // METER electrical at:room_name
    private static final Pattern METER_PATTERN = Pattern.compile(
        "METER\\s+(electrical|water|gas)\\s+at:(\\w+)"
    );
    // RISER "name" type:plumbing at:location
    private static final Pattern RISER_PATTERN = Pattern.compile(
        "RISER\\s+\"([^\"]+)\"(?:\\s+type:(electrical|plumbing))?\\s+at:(\\w+)"
    );
    // adjacent_unit: constraint (party wall)
    private static final Pattern ADJACENT_UNIT_PATTERN = Pattern.compile(
        "adjacent_unit:\\s*(\\w+)"
    );

    /**
     * Parse BUILDING DSL input.
     */
    public static BuildingDefinition parse(String dsl) {
        // Find BUILDING block
        int buildingStart = dsl.indexOf("BUILDING");
        if (buildingStart < 0) {
            throw new IllegalArgumentException("No BUILDING found in DSL");
        }

        // Extract building name
        int nameStart = dsl.indexOf('"', buildingStart) + 1;
        int nameEnd = dsl.indexOf('"', nameStart);
        String buildingName = dsl.substring(nameStart, nameEnd);

        // Phase 28: Extract profile, protocol, lod from header (before opening brace)
        int braceStart = dsl.indexOf('{', nameEnd);
        String header = dsl.substring(nameEnd, braceStart);

        String profile = null;
        Matcher profileMatcher = PROFILE_PATTERN.matcher(header);
        if (profileMatcher.find()) {
            profile = profileMatcher.group(1);
        }

        String protocol = null;
        Matcher protocolMatcher = PROTOCOL_PATTERN.matcher(header);
        if (protocolMatcher.find()) {
            protocol = protocolMatcher.group(1);
        }

        int lod = 300; // Default LOD
        Matcher lodMatcher = LOD_PATTERN.matcher(header);
        if (lodMatcher.find()) {
            lod = Integer.parseInt(lodMatcher.group(1));
        }

        // Phase 50B.1: Extract construction system
        ConstructionSystem constructionSystem = ConstructionSystem.FRAMED; // Default
        Matcher constructionMatcher = CONSTRUCTION_PATTERN.matcher(header);
        if (constructionMatcher.find()) {
            constructionSystem = ConstructionSystem.fromKeyword(constructionMatcher.group(1));
        }

        // Phase 46: Extract building type
        BuildingType buildingType = BuildingType.SINGLE_UNIT;
        Matcher buildingTypeMatcher = BUILDING_TYPE_PATTERN.matcher(header);
        if (buildingTypeMatcher.find()) {
            buildingType = BuildingType.fromKeyword(buildingTypeMatcher.group(1));
        }

        // Find building content between braces
        String buildingContent = extractBlock(dsl, nameEnd);

        // Phase 46: Check for UNIT blocks (auto-detect multi-unit)
        boolean hasUnitBlocks = UNIT_PATTERN.matcher(buildingContent).find();
        if (hasUnitBlocks) {
            buildingType = BuildingType.MULTI_UNIT;
        }

        // Phase 46: Parse multi-unit building
        if (buildingType == BuildingType.MULTI_UNIT) {
            return parseMultiUnit(buildingName, buildingType, buildingContent,
                                  profile, protocol, lod, constructionSystem);
        }

        // Single-unit building: parse storeys directly
        List<StoreyDef> storeys = new ArrayList<>();
        int pos = 0;
        while (true) {
            int storeyStart = buildingContent.indexOf("STOREY", pos);
            if (storeyStart < 0) break;

            List<StoreyDef> expanded = parseStorey(buildingContent, storeyStart);
            storeys.addAll(expanded);

            // Move past this storey block
            int storeyBraceStart = buildingContent.indexOf('{', storeyStart);
            String storeyContent = extractBlock(buildingContent, storeyBraceStart - 1);
            pos = storeyBraceStart + storeyContent.length() + 2; // +2 for braces
        }

        // Sort by level
        storeys.sort((a, b) -> Integer.compare(a.level(), b.level()));

        // Parse roof (Phase 26: with optional overhang)
        RoofDef roof = null;
        Matcher roofMatcher = ROOF_PATTERN.matcher(buildingContent);
        if (roofMatcher.find()) {
            double pitch = Double.parseDouble(roofMatcher.group(1));
            double overhang = roofMatcher.group(2) != null ?
                Double.parseDouble(roofMatcher.group(2)) : 0;
            roof = new RoofDef(pitch, overhang);
        }

        // Phase 26: Parse GRID block
        GridDef grid = parseGrid(buildingContent);

        // Phase 26: Parse ENVELOPE block
        EnvelopeDef envelope = parseEnvelope(buildingContent, roof);

        // Phase 28: Parse SCHEDULE blocks
        ScheduleDef doorSchedule = parseSchedule(buildingContent, "doors");
        ScheduleDef windowSchedule = parseSchedule(buildingContent, "windows");

        // Phase 56B: Parse CORE block (building-level vertical circulation)
        CoreDef core = parseCore(buildingContent);

        return new BuildingDefinition(buildingName, BuildingType.SINGLE_UNIT, storeys,
                                      List.of(), SharedDefinition.EMPTY, core,
                                      roof, grid, envelope,
                                      doorSchedule, windowSchedule, profile, protocol, lod, constructionSystem);
    }

    /**
     * Phase 46: Parse multi-unit building.
     */
    private static BuildingDefinition parseMultiUnit(
            String buildingName,
            BuildingType buildingType,
            String buildingContent,
            String profile,
            String protocol,
            int lod,
            ConstructionSystem constructionSystem) {

        // Parse UNIT blocks
        List<UnitDefinition> units = new ArrayList<>();
        Matcher unitMatcher = UNIT_PATTERN.matcher(buildingContent);
        while (unitMatcher.find()) {
            int unitStart = unitMatcher.start();
            String unitName = unitMatcher.group(1);
            UnitType unitType = UnitType.fromKeyword(unitMatcher.group(2));
            EntryType entryType = EntryType.fromKeyword(unitMatcher.group(3));

            UnitDefinition unit = parseUnit(buildingContent, unitStart, unitName, unitType, entryType);
            units.add(unit);
        }

        // Parse SHARED block
        SharedDefinition shared = SharedDefinition.EMPTY;
        Matcher sharedMatcher = SHARED_PATTERN.matcher(buildingContent);
        if (sharedMatcher.find()) {
            shared = parseShared(buildingContent, sharedMatcher.start());
        }

        // Parse roof
        RoofDef roof = null;
        Matcher roofMatcher = ROOF_PATTERN.matcher(buildingContent);
        if (roofMatcher.find()) {
            double pitch = Double.parseDouble(roofMatcher.group(1));
            double overhang = roofMatcher.group(2) != null ?
                Double.parseDouble(roofMatcher.group(2)) : 0;
            roof = new RoofDef(pitch, overhang);
        }

        // Parse grid
        GridDef grid = parseGrid(buildingContent);

        // Parse envelope
        EnvelopeDef envelope = parseEnvelope(buildingContent, roof);

        // Parse schedules
        ScheduleDef doorSchedule = parseSchedule(buildingContent, "doors");
        ScheduleDef windowSchedule = parseSchedule(buildingContent, "windows");

        // Phase 56B: Parse CORE block
        CoreDef core = parseCore(buildingContent);

        return new BuildingDefinition(buildingName, buildingType, List.of(), units, shared, core,
                                      roof, grid, envelope,
                                      doorSchedule, windowSchedule, profile, protocol, lod, constructionSystem);
    }

    /**
     * Phase 46: Parse UNIT block.
     */
    private static UnitDefinition parseUnit(String content, int start,
                                            String unitName, UnitType unitType, EntryType entryType) {
        // Extract unit content
        int braceStart = content.indexOf('{', start);
        String unitContent = extractBlock(content, braceStart - 1);

        // Parse storeys within unit
        List<StoreyDef> storeys = new ArrayList<>();
        int pos = 0;
        while (true) {
            int storeyStart = unitContent.indexOf("STOREY", pos);
            if (storeyStart < 0) break;

            List<StoreyDef> expanded = parseStorey(unitContent, storeyStart);
            storeys.addAll(expanded);

            // Move past this storey block
            int storeyBraceStart = unitContent.indexOf('{', storeyStart);
            String storeyContent = extractBlock(unitContent, storeyBraceStart - 1);
            pos = storeyBraceStart + storeyContent.length() + 2;
        }
        storeys.sort((a, b) -> Integer.compare(a.level(), b.level()));

        // Parse meters
        List<MeterDef> meters = new ArrayList<>();
        Matcher meterMatcher = METER_PATTERN.matcher(unitContent);
        while (meterMatcher.find()) {
            MeterDef.MeterType meterType = MeterDef.typeFromKeyword(meterMatcher.group(1));
            String location = meterMatcher.group(2);
            meters.add(new MeterDef(meterType, location));
        }

        return new UnitDefinition(unitName, unitType, entryType, storeys, meters);
    }

    /**
     * Phase 46: Parse SHARED block.
     */
    private static SharedDefinition parseShared(String content, int start) {
        // Extract shared content
        int braceStart = content.indexOf('{', start);
        String sharedContent = extractBlock(content, braceStart - 1);

        // Parse storeys within shared
        List<StoreyDef> storeys = new ArrayList<>();
        int pos = 0;
        while (true) {
            int storeyStart = sharedContent.indexOf("STOREY", pos);
            if (storeyStart < 0) break;

            List<StoreyDef> expanded = parseStorey(sharedContent, storeyStart);
            storeys.addAll(expanded);

            int storeyBraceStart = sharedContent.indexOf('{', storeyStart);
            String storeyContent = extractBlock(sharedContent, storeyBraceStart - 1);
            pos = storeyBraceStart + storeyContent.length() + 2;
        }
        storeys.sort((a, b) -> Integer.compare(a.level(), b.level()));

        // Parse risers
        List<RiserDef> risers = new ArrayList<>();
        Matcher riserMatcher = RISER_PATTERN.matcher(sharedContent);
        while (riserMatcher.find()) {
            String riserName = riserMatcher.group(1);
            RiserDef.RiserType riserType = RiserDef.typeFromKeyword(riserMatcher.group(2));
            String location = riserMatcher.group(3);
            risers.add(new RiserDef(riserName, riserType, location));
        }

        if (storeys.isEmpty() && risers.isEmpty()) {
            return SharedDefinition.EMPTY;
        }
        return new SharedDefinition(storeys, risers);
    }

    /**
     * Phase 28: Parse SCHEDULE block.
     * Syntax: SCHEDULE doors { D1: 900x2100 "description" ... }
     */
    private static ScheduleDef parseSchedule(String content, String category) {
        // Find SCHEDULE <category> block
        Pattern schedulePattern = Pattern.compile(
            "SCHEDULE\\s+" + category + "\\s*\\{",
            Pattern.CASE_INSENSITIVE
        );
        Matcher scheduleMatcher = schedulePattern.matcher(content);

        if (!scheduleMatcher.find()) return null;

        int scheduleStart = scheduleMatcher.start();
        String scheduleContent = extractBlock(content, scheduleStart);

        if (scheduleContent.isEmpty()) return null;

        // Parse entries: D1: 900x2100 "description"
        List<ScheduleEntryDef> entries = new ArrayList<>();
        Matcher entryMatcher = SCHEDULE_ENTRY_PATTERN.matcher(scheduleContent);

        while (entryMatcher.find()) {
            String typeCode = entryMatcher.group(1);
            double widthMm = Double.parseDouble(entryMatcher.group(2));
            double heightMm = Double.parseDouble(entryMatcher.group(3));
            String description = entryMatcher.group(4); // may be null

            entries.add(new ScheduleEntryDef(typeCode, widthMm, heightMm, description));
        }

        return entries.isEmpty() ? null : new ScheduleDef(category, entries);
    }

    /**
     * Phase 56B: Parse CORE block (building-level vertical circulation).
     * Syntax: CORE "main_core" bounds:C1-D6 { STAIR... ELEVATOR_LOBBY... SHAFT... }
     */
    private static CoreDef parseCore(String content) {
        Matcher coreMatcher = CORE_PATTERN.matcher(content);
        if (!coreMatcher.find()) return null;

        String name = coreMatcher.group(1);
        String bounds = coreMatcher.group(2);

        // Extract CORE block content
        int coreStart = coreMatcher.start();
        int braceIdx = content.indexOf('{', coreStart);
        if (braceIdx < 0) return null;

        String coreContent = extractBlock(content, braceIdx - 1);
        if (coreContent.isEmpty()) return null;

        // Parse stairs within CORE
        List<StairDef> stairs = new ArrayList<>();
        Matcher stairMatcher = STAIR_EXTENDED_PATTERN.matcher(coreContent);
        while (stairMatcher.find()) {
            String stairType = stairMatcher.group(4);  // PROTECTED, etc.
            boolean pressurized = "true".equalsIgnoreCase(stairMatcher.group(5));

            // Phase 82: Parse fire_rating from optional block content
            double fireRatingHr = 0.0;
            int afterMatch = stairMatcher.end();
            int nextBrace = coreContent.indexOf('{', afterMatch);
            if (nextBrace >= 0 && nextBrace - afterMatch < 20) {
                String stairBlock = extractBlock(coreContent, afterMatch);
                java.util.regex.Matcher frMatcher = java.util.regex.Pattern.compile(
                    "fire_rating:\\s*(\\d+)hr").matcher(stairBlock);
                if (frMatcher.find()) {
                    fireRatingHr = Double.parseDouble(frMatcher.group(1));
                }
            }

            stairs.add(new StairDef(
                stairMatcher.group(1),  // name
                stairMatcher.group(2),  // gridPosition
                Double.parseDouble(stairMatcher.group(3)),  // width
                stairType,
                pressurized,
                fireRatingHr
            ));
        }

        // Parse elevator lobbies within CORE
        List<ElevatorLobbyDef> lobbies = new ArrayList<>();
        Matcher lobbyMatcher = ELEVATOR_LOBBY_PATTERN.matcher(coreContent);
        while (lobbyMatcher.find()) {
            String lobbyName = lobbyMatcher.group(1);
            String lobbyBounds = lobbyMatcher.group(2);
            boolean pressurized = "true".equalsIgnoreCase(lobbyMatcher.group(3));
            double fireRating = lobbyMatcher.group(4) != null ?
                Double.parseDouble(lobbyMatcher.group(4)) : 1.0;

            // Parse elevators within this lobby
            int lobbyStart = lobbyMatcher.start();
            int lobbyBraceIdx = coreContent.indexOf('{', lobbyStart);
            List<ElevatorDef> elevators = new ArrayList<>();
            if (lobbyBraceIdx >= 0) {
                String lobbyBlock = extractBlock(coreContent, lobbyBraceIdx - 1);
                Matcher elevMatcher = ELEVATOR_PATTERN.matcher(lobbyBlock);
                while (elevMatcher.find()) {
                    // Phase 82: Parse optional block for emergency_power and fire_rating
                    boolean emergPower = false;
                    double elevFireRating = fireRating;
                    int afterElev = elevMatcher.end();
                    int elevBrace = lobbyBlock.indexOf('{', afterElev);
                    if (elevBrace >= 0 && elevBrace - afterElev < 20) {
                        String elevBlock = extractBlock(lobbyBlock, afterElev);
                        emergPower = elevBlock.contains("emergency_power: true")
                                  || elevBlock.contains("emergency_power:true");
                        java.util.regex.Matcher frMatcher = java.util.regex.Pattern.compile(
                            "fire_rating:\\s*(\\d+)hr").matcher(elevBlock);
                        if (frMatcher.find()) {
                            elevFireRating = Double.parseDouble(frMatcher.group(1));
                        }
                    }

                    elevators.add(new ElevatorDef(
                        elevMatcher.group(1),  // name
                        elevMatcher.group(2),  // type
                        null,                   // gridPosition
                        Integer.parseInt(elevMatcher.group(3)),  // carWidth
                        Integer.parseInt(elevMatcher.group(4)),  // carDepth
                        Integer.parseInt(elevMatcher.group(5)),  // doorWidth
                        emergPower,
                        elevFireRating
                    ));
                }
            }

            lobbies.add(new ElevatorLobbyDef(lobbyName, lobbyBounds, pressurized, fireRating, elevators));
        }

        // Parse shafts within CORE
        List<ShaftDef> shafts = new ArrayList<>();
        Matcher shaftMatcher = SHAFT_PATTERN.matcher(coreContent);
        while (shaftMatcher.find()) {
            shafts.add(new ShaftDef(
                shaftMatcher.group(1),  // name
                shaftMatcher.group(5),  // type (ELECTRICAL, PLUMBING)
                shaftMatcher.group(2),  // gridPosition
                Double.parseDouble(shaftMatcher.group(3)),  // width
                Double.parseDouble(shaftMatcher.group(4))   // depth
            ));
        }

        return new CoreDef(name, bounds, stairs, lobbies, shafts);
    }

    /**
     * Phase 26: Parse GRID block.
     * Syntax: GRID { axes: A,B,C,D,E / 2,3,4,5  spacing: from_pdf }
     *    or:  GRID { axes: A,B,C,D,E / 2,3,4,5  spacing: 3.0,2.5,3.0,2.0 / 2.0,3.0,2.5 }
     */
    private static GridDef parseGrid(String content) {
        int gridStart = content.indexOf("GRID");
        if (gridStart < 0) return null;

        String gridContent = extractBlock(content, gridStart);
        if (gridContent.isEmpty()) return null;

        // Parse axes: A,B,C,D,E / 2,3,4,5
        Pattern axesPattern = Pattern.compile("axes:\\s*([A-Za-z,\\s]+)\\s*/\\s*([0-9,\\s]+)");
        Matcher axesMatcher = axesPattern.matcher(gridContent);

        List<String> xAxes = new ArrayList<>();
        List<String> yAxes = new ArrayList<>();

        if (axesMatcher.find()) {
            // Parse X axes (letters)
            String xPart = axesMatcher.group(1);
            for (String axis : xPart.split(",")) {
                String trimmed = axis.trim();
                if (!trimmed.isEmpty()) xAxes.add(trimmed);
            }
            // Parse Y axes (numbers)
            String yPart = axesMatcher.group(2);
            for (String axis : yPart.split(",")) {
                String trimmed = axis.trim();
                if (!trimmed.isEmpty()) yAxes.add(trimmed);
            }
        }

        // Parse spacing
        Pattern spacingPattern = Pattern.compile("spacing:\\s*(from_pdf|[0-9.,/\\s]+)");
        Matcher spacingMatcher = spacingPattern.matcher(gridContent);

        boolean fromPdf = false;
        List<Double> xSpacing = new ArrayList<>();
        List<Double> ySpacing = new ArrayList<>();

        if (spacingMatcher.find()) {
            String spacingStr = spacingMatcher.group(1).trim();
            if (spacingStr.equals("from_pdf")) {
                fromPdf = true;
            } else if (spacingStr.contains("/")) {
                // Explicit spacing: 3.0,2.5,3.0 / 2.0,3.0,2.5
                String[] parts = spacingStr.split("/");
                if (parts.length >= 1) {
                    for (String s : parts[0].split(",")) {
                        String trimmed = s.trim();
                        if (!trimmed.isEmpty()) xSpacing.add(Double.parseDouble(trimmed));
                    }
                }
                if (parts.length >= 2) {
                    for (String s : parts[1].split(",")) {
                        String trimmed = s.trim();
                        if (!trimmed.isEmpty()) ySpacing.add(Double.parseDouble(trimmed));
                    }
                }
            }
        }

        return new GridDef(xAxes, yAxes, xSpacing, ySpacing, fromPdf);
    }

    /**
     * Phase 26/28: Parse ENVELOPE block.
     * Syntax: ENVELOPE {
     *     FOUNDATION type:slab depth:300mm
     *     DRAINAGE { PERIMETER_DRAIN offset:600mm connects:municipal }
     *     ROOF pitch:25deg overhang:600mm  // Optional, usually at building level
     * }
     */
    private static EnvelopeDef parseEnvelope(String content, RoofDef roof) {
        int envStart = content.indexOf("ENVELOPE");
        if (envStart < 0) return null;

        String envContent = extractBlock(content, envStart);
        if (envContent.isEmpty()) return null;

        // Phase 28: Parse FOUNDATION
        FoundationDef foundation = parseFoundation(envContent);

        // Parse DRAINAGE block
        DrainageDef drainage = null;
        int drainStart = envContent.indexOf("DRAINAGE");
        if (drainStart >= 0 || envContent.contains("PERIMETER_DRAIN")) {
            // Parse PERIMETER_DRAIN offset
            Pattern drainPattern = Pattern.compile("PERIMETER_DRAIN\\s+offset:(\\d+)mm");
            Matcher drainMatcher = drainPattern.matcher(envContent);

            double offset = roof != null ? roof.overhangMm() : 600; // Default to roof overhang
            if (drainMatcher.find()) {
                offset = Double.parseDouble(drainMatcher.group(1));
            }

            // Parse connects
            Pattern connectsPattern = Pattern.compile("connects:\\s*(\\w+)");
            Matcher connectsMatcher = connectsPattern.matcher(envContent);
            String connects = connectsMatcher.find() ? connectsMatcher.group(1) : "municipal_drain";

            // Parse downpipe locations
            Pattern downpipePattern = Pattern.compile("DOWNPIPE\\s+at:([A-Za-z0-9,\\s]+)");
            Matcher downpipeMatcher = downpipePattern.matcher(envContent);
            List<String> downpipes = new ArrayList<>();
            if (downpipeMatcher.find()) {
                for (String loc : downpipeMatcher.group(1).split(",")) {
                    String trimmed = loc.trim();
                    if (!trimmed.isEmpty()) downpipes.add(trimmed);
                }
            }

            drainage = new DrainageDef(offset, connects, downpipes);
        }

        // Return envelope with foundation, drainage, and roof reference for correlation
        if (foundation != null || drainage != null) {
            return new EnvelopeDef(foundation, drainage, roof);
        }
        return null;
    }

    /**
     * Phase 28: Parse FOUNDATION block.
     * Syntax: FOUNDATION type:slab depth:300mm thickness:150mm
     *    or:  FOUNDATION slab:150mm  (simplified)
     */
    private static FoundationDef parseFoundation(String content) {
        // Try full syntax: FOUNDATION type:slab depth:300mm thickness:150mm
        Pattern fullPattern = Pattern.compile(
            "FOUNDATION\\s+type:(\\w+)(?:\\s+depth:(\\d+)mm)?(?:\\s+thickness:(\\d+)mm)?"
        );
        Matcher fullMatcher = fullPattern.matcher(content);

        if (fullMatcher.find()) {
            String typeStr = fullMatcher.group(1).toUpperCase();
            double depth = fullMatcher.group(2) != null ? Double.parseDouble(fullMatcher.group(2)) : 300;
            double thickness = fullMatcher.group(3) != null ? Double.parseDouble(fullMatcher.group(3)) : 150;

            FoundationDef.FoundationType type = switch (typeStr) {
                case "SLAB", "SLAB_ON_GRADE" -> FoundationDef.FoundationType.SLAB_ON_GRADE;
                case "STRIP", "STRIP_FOOTING" -> FoundationDef.FoundationType.STRIP_FOOTING;
                case "PAD", "PAD_FOOTING" -> FoundationDef.FoundationType.PAD_FOOTING;
                case "RAFT", "MAT" -> FoundationDef.FoundationType.RAFT;
                case "PILED", "PILE" -> FoundationDef.FoundationType.PILED;
                default -> FoundationDef.FoundationType.SLAB_ON_GRADE;
            };

            return new FoundationDef(type, depth, thickness, "Concrete");
        }

        // Try simplified syntax: FOUNDATION slab:150mm
        Pattern simplePattern = Pattern.compile("FOUNDATION\\s+slab:(\\d+)mm");
        Matcher simpleMatcher = simplePattern.matcher(content);

        if (simpleMatcher.find()) {
            double thickness = Double.parseDouble(simpleMatcher.group(1));
            return new FoundationDef(
                FoundationDef.FoundationType.SLAB_ON_GRADE,
                300,  // Default depth
                thickness,
                "Concrete"
            );
        }

        return null;
    }

    private static List<StoreyDef> parseStorey(String content, int start) {
        // Extract: STOREY "name" level:N height:Xm [repeat:X-Y] {
        // Phase 56B: Added optional repeat:X-Y for typical floor expansion
        Pattern headerPattern = Pattern.compile(
            "STOREY\\s+\"([^\"]+)\"\\s+level:(\\d+)\\s+height:([\\d.]+)m(?:\\s+repeat:(\\d+)-(\\d+))?"
        );

        Matcher header = headerPattern.matcher(content.substring(start));
        if (!header.find()) {
            throw new IllegalArgumentException("Invalid STOREY syntax at " + start);
        }

        String name = header.group(1);
        int level = Integer.parseInt(header.group(2));
        double height = Double.parseDouble(header.group(3));

        // Phase 56B: Parse optional repeat range
        Integer repeatStart = header.group(4) != null ? Integer.parseInt(header.group(4)) : null;
        Integer repeatEnd = header.group(5) != null ? Integer.parseInt(header.group(5)) : null;

        // Extract storey content
        int braceStart = content.indexOf('{', start);
        String storeyContent = extractBlock(content, braceStart - 1);

        // Parse rooms
        // Phase 25: Use regex to catch ANY room type, not just known ones
        // This allows unknown types to pass through to RoomType.fromKeyword() for outlier handling
        List<RoomDef> rooms = new ArrayList<>();

        // Pattern matches: ROOMTYPE "name" - captures any uppercase identifier
        Pattern roomPattern = Pattern.compile("([A-Z][A-Z0-9_]*)\\s+\"([^\"]+)\"");
        Matcher roomMatcher = roomPattern.matcher(storeyContent);

        // Keywords to skip (not room types)
        // Note: SPACE is NOT skipped - it's the Phase 26 universal primitive
        // Phase 56: Added ELEVATOR, SHAFT, CORE
        // Phase 86: ELEVATOR_LOBBY/LIFT_LOBBY removed from skip — parsed as rooms
        //           so they participate in shared-edge wall generation (corridor↔lobby walls)
        Set<String> skipKeywords = Set.of(
            "STOREY", "STAIR", "LANDING", "ROOF", "DOOR", "WINDOW",
            "SPRINKLERS", "LIGHTS", "BUILDING", "GRID", "ENVELOPE",
            "DRAINAGE", "PERIMETER_DRAIN", "GUTTER", "DOWNPIPE",
            "ELEVATOR", "SHAFT", "CORE",
            "MEP_PROFILE", "FIRE_PROTECTION", "ELECTRICAL", "PLUMBING",
            "MEP_CONNECTION", "SCHEDULE", "UNIT"
        );

        while (roomMatcher.find()) {
            String roomType = roomMatcher.group(1);

            // Skip known non-room keywords
            if (skipKeywords.contains(roomType)) {
                continue;
            }

            int roomStart = roomMatcher.start();
            RoomDef room = parseRoom(storeyContent, roomStart, roomType);
            if (room != null) {
                rooms.add(room);
            }
        }

        // Parse stairs
        List<StairDef> stairs = new ArrayList<>();
        Matcher stairMatcher = STAIR_PATTERN.matcher(storeyContent);
        while (stairMatcher.find()) {
            stairs.add(new StairDef(
                stairMatcher.group(1),
                stairMatcher.group(2),
                Double.parseDouble(stairMatcher.group(3)),
                stairMatcher.group(4)
            ));
        }

        // Parse landings
        List<LandingDef> landings = new ArrayList<>();
        Matcher landingMatcher = LANDING_PATTERN.matcher(storeyContent);
        while (landingMatcher.find()) {
            landings.add(new LandingDef(
                landingMatcher.group(1),
                landingMatcher.group(2),
                Double.parseDouble(landingMatcher.group(3)),
                Double.parseDouble(landingMatcher.group(4)),
                landingMatcher.group(5)
            ));
        }

        // Phase 56: Parse elevators
        List<ElevatorDef> elevators = new ArrayList<>();
        Matcher elevatorMatcher = ELEVATOR_PATTERN.matcher(storeyContent);
        while (elevatorMatcher.find()) {
            elevators.add(new ElevatorDef(
                elevatorMatcher.group(1),  // name
                elevatorMatcher.group(2),  // type
                null,                       // gridPosition (parsed from context)
                Integer.parseInt(elevatorMatcher.group(3)),  // carWidth
                Integer.parseInt(elevatorMatcher.group(4)),  // carDepth
                Integer.parseInt(elevatorMatcher.group(5)),  // doorWidth
                false,                      // emergencyPower (TODO: parse)
                1.0                         // fireRating (TODO: parse)
            ));
        }

        // Phase 56: Parse elevator lobbies
        List<ElevatorLobbyDef> lobbies = new ArrayList<>();
        Matcher lobbyMatcher = ELEVATOR_LOBBY_PATTERN.matcher(storeyContent);
        while (lobbyMatcher.find()) {
            String lobbyName = lobbyMatcher.group(1);
            String bounds = lobbyMatcher.group(2);
            boolean pressurized = "true".equalsIgnoreCase(lobbyMatcher.group(3));
            double fireRating = lobbyMatcher.group(4) != null ?
                Double.parseDouble(lobbyMatcher.group(4)) : 1.0;

            // Parse elevators within this lobby block
            List<ElevatorDef> lobbyElevators = new ArrayList<>();
            int lobbyStart = lobbyMatcher.start();
            int braceIdx = storeyContent.indexOf('{', lobbyStart);
            if (braceIdx >= 0) {
                String lobbyBlock = extractBlock(storeyContent, braceIdx - 1);
                Matcher innerElevMatcher = ELEVATOR_PATTERN.matcher(lobbyBlock);
                while (innerElevMatcher.find()) {
                    lobbyElevators.add(new ElevatorDef(
                        innerElevMatcher.group(1),
                        innerElevMatcher.group(2),
                        null,
                        Integer.parseInt(innerElevMatcher.group(3)),
                        Integer.parseInt(innerElevMatcher.group(4)),
                        Integer.parseInt(innerElevMatcher.group(5)),
                        false, 1.0
                    ));
                }
            }

            lobbies.add(new ElevatorLobbyDef(
                lobbyName, bounds, pressurized, fireRating, lobbyElevators
            ));
        }

        // Phase 56: Parse shafts
        List<ShaftDef> shafts = new ArrayList<>();
        Matcher shaftMatcher = SHAFT_PATTERN.matcher(storeyContent);
        while (shaftMatcher.find()) {
            shafts.add(new ShaftDef(
                shaftMatcher.group(1),  // name
                shaftMatcher.group(5),  // type
                shaftMatcher.group(2),  // gridPosition
                Double.parseDouble(shaftMatcher.group(3)),  // width
                Double.parseDouble(shaftMatcher.group(4))   // depth
            ));
        }

        // Phase 56B: Expand repeat range into multiple storeys
        List<StoreyDef> result = new ArrayList<>();

        if (repeatStart != null && repeatEnd != null) {
            // Create one storey for each level in the repeat range
            for (int lvl = repeatStart; lvl <= repeatEnd; lvl++) {
                result.add(new StoreyDef(
                    name + "_F" + lvl,  // e.g., "Typical_F2", "Typical_F3"
                    lvl,
                    height,
                    rooms,      // Same room layout for all typical floors
                    stairs,
                    landings,
                    elevators,
                    lobbies,
                    shafts
                ));
            }
        } else {
            // Single storey (no repeat)
            result.add(new StoreyDef(name, level, height, rooms, stairs, landings,
                                    elevators, lobbies, shafts));
        }

        return result;
    }

    private static RoomDef parseRoom(String content, int start, String roomType) {
        // Try explicit position first: ROOMTYPE "name" at:XX size:WxDm {
        Pattern explicitPattern = Pattern.compile(
            roomType + "\\s+\"([^\"]+)\"\\s+at:(\\w+(?:-\\w+)?)\\s+size:([\\d.]+)x([\\d.]+)m"
        );

        // Constraint mode (no at:): ROOMTYPE "name" size:WxDm {
        Pattern constraintPattern = Pattern.compile(
            roomType + "\\s+\"([^\"]+)\"\\s+size:([\\d.]+)x([\\d.]+)m"
        );

        // Phase 26: Grid bounds mode: ROOMTYPE "name" bounds:A2-B4 {
        // or: SPACE "name" type:LIVING bounds:A2-B4 {
        Pattern boundsPattern = Pattern.compile(
            roomType + "\\s+\"([^\"]+)\"(?:\\s+type:(\\w+))?\\s+bounds:([A-Za-z]\\d+-[A-Za-z]\\d+)"
        );

        Matcher explicitMatcher = explicitPattern.matcher(content.substring(start));
        Matcher constraintMatcher = constraintPattern.matcher(content.substring(start));
        Matcher boundsMatcher = boundsPattern.matcher(content.substring(start));

        String name;
        String gridPos;
        double width, depth;
        int headerEnd;
        String gridBoundsFromHeader = null;
        String typeOverride = null;

        if (explicitMatcher.find()) {
            // Mode A: Explicit position
            name = explicitMatcher.group(1);
            gridPos = explicitMatcher.group(2);
            width = Double.parseDouble(explicitMatcher.group(3));
            depth = Double.parseDouble(explicitMatcher.group(4));
            headerEnd = explicitMatcher.end();
        } else if (boundsMatcher.find()) {
            // Mode C: Grid bounds (Phase 26)
            name = boundsMatcher.group(1);
            typeOverride = boundsMatcher.group(2); // Optional type: override for SPACE
            gridBoundsFromHeader = boundsMatcher.group(3);
            gridPos = null;  // Position from grid bounds
            width = 0;       // Dimensions from grid (resolved later)
            depth = 0;
            headerEnd = boundsMatcher.end();
        } else if (constraintMatcher.find()) {
            // Mode B: Constraint-based (no at:)
            name = constraintMatcher.group(1);
            gridPos = null;  // Solver will determine position
            width = Double.parseDouble(constraintMatcher.group(2));
            depth = Double.parseDouble(constraintMatcher.group(3));
            headerEnd = constraintMatcher.end();
        } else {
            return null;
        }

        // Use type override if provided (for SPACE "name" type:LIVING syntax)
        String finalRoomType = typeOverride != null ? typeOverride : roomType;

        // Extract room content
        int braceStart = content.indexOf('{', start + headerEnd);
        if (braceStart < 0) {
            // No braces - minimal room definition
            return new RoomDef(finalRoomType, name, gridPos, width, depth, List.of(),
                              null, null, List.of(), List.of(), null, null, null, null, null,
                              gridBoundsFromHeader, null);
        }

        String roomContent = extractBlock(content, braceStart - 1);

        // Parse openings
        List<OpeningDef> openings = new ArrayList<>();

        Matcher doorMatcher = DOOR_PATTERN.matcher(roomContent);
        while (doorMatcher.find()) {
            String wall = doorMatcher.group(1);
            String connectsTo = doorMatcher.group(2);
            double w = doorMatcher.group(3) != null ? Integer.parseInt(doorMatcher.group(3)) / 1000.0 : 0.9;
            double h = doorMatcher.group(4) != null ? Integer.parseInt(doorMatcher.group(4)) / 1000.0 : 2.1;
            openings.add(new OpeningDef("DOOR", wall, connectsTo, w, h));
        }

        Matcher windowMatcher = WINDOW_PATTERN.matcher(roomContent);
        while (windowMatcher.find()) {
            String wall = windowMatcher.group(1);
            double w = windowMatcher.group(2) != null ? Integer.parseInt(windowMatcher.group(2)) / 1000.0 : 1.2;
            double h = windowMatcher.group(3) != null ? Integer.parseInt(windowMatcher.group(3)) / 1000.0 : 1.0;
            openings.add(new OpeningDef("WINDOW", wall, null, w, h));
        }

        // Parse sprinklers (Phase 14B)
        Double sprinklerSpacing = null;
        Matcher sprinklerMatcher = SPRINKLER_PATTERN.matcher(roomContent);
        if (sprinklerMatcher.find()) {
            sprinklerSpacing = Double.parseDouble(sprinklerMatcher.group(1));
        }

        // Parse lights (Phase 14B)
        Double lightSpacing = null;
        Matcher lightMatcher = LIGHT_PATTERN.matcher(roomContent);
        if (lightMatcher.find()) {
            lightSpacing = Double.parseDouble(lightMatcher.group(1));
        }

        // Parse constraints (Layer 3)
        List<String> adjacentTo = new ArrayList<>();
        Matcher adjacentMatcher = ADJACENT_PATTERN.matcher(roomContent);
        while (adjacentMatcher.find()) {
            adjacentTo.add(adjacentMatcher.group(1));
        }

        List<String> notAdjacentTo = new ArrayList<>();
        Matcher notAdjacentMatcher = NOT_ADJACENT_PATTERN.matcher(roomContent);
        while (notAdjacentMatcher.find()) {
            notAdjacentTo.add(notAdjacentMatcher.group(1));
        }

        String exteriorWall = null;
        Matcher exteriorMatcher = EXTERIOR_PATTERN.matcher(roomContent);
        if (exteriorMatcher.find()) {
            exteriorWall = exteriorMatcher.group(1);
        }

        // Phase 16: Vertical alignment constraint
        String alignsWith = null;
        Matcher alignsMatcher = ALIGNS_PATTERN.matcher(roomContent);
        if (alignsMatcher.find()) {
            alignsWith = alignsMatcher.group(1);
        }

        // Phase 17: Extended vertical vocabulary
        String above = null;
        Matcher aboveMatcher = ABOVE_PATTERN.matcher(roomContent);
        if (aboveMatcher.find()) {
            above = aboveMatcher.group(1);
        }

        String below = null;
        Matcher belowMatcher = BELOW_PATTERN.matcher(roomContent);
        if (belowMatcher.find()) {
            below = belowMatcher.group(1);
        }

        String stack = null;
        Matcher stackMatcher = STACK_PATTERN.matcher(roomContent);
        if (stackMatcher.find()) {
            stack = stackMatcher.group(1);
        }

        // Phase 26: Grid bounds (bounds:A2-B4)
        // Prefer bounds from header pattern, fall back to content pattern
        String gridBounds = gridBoundsFromHeader;
        if (gridBounds == null) {
            Matcher gridBoundsMatcher = GRID_BOUNDS_PATTERN.matcher(roomContent);
            if (gridBoundsMatcher.find()) {
                gridBounds = gridBoundsMatcher.group(1);
            }
        }

        // Phase 26: Porch roof type (roof:ATTACHED/SEPARATE)
        BuildingDefinition.PorchRoofType porchRoofType = null;
        Matcher porchRoofMatcher = PORCH_ROOF_PATTERN.matcher(roomContent);
        if (porchRoofMatcher.find()) {
            String roofTypeStr = porchRoofMatcher.group(1);
            porchRoofType = BuildingDefinition.PorchRoofType.valueOf(roofTypeStr);
        }

        // Phase 27: opens_to constraint (implies door connection)
        String opensTo = null;
        Matcher opensToMatcher = OPENS_TO_PATTERN.matcher(roomContent);
        if (opensToMatcher.find()) {
            opensTo = opensToMatcher.group(1);
        }

        // Phase 27: zones list for OPEN_PLAN (zones: LIVING, DINING, KITCHEN)
        List<String> zones = new ArrayList<>();
        Matcher zonesMatcher = ZONES_PATTERN.matcher(roomContent);
        if (zonesMatcher.find()) {
            String zonesStr = zonesMatcher.group(1);
            for (String zone : zonesStr.split(",")) {
                String trimmed = zone.trim();
                if (!trimmed.isEmpty()) zones.add(trimmed);
            }
        }

        // Phase 27: Multiple exterior walls
        List<String> exteriorWalls = new ArrayList<>();
        Matcher extWallsMatcher = EXTERIOR_PATTERN.matcher(roomContent);
        while (extWallsMatcher.find()) {
            String wall = extWallsMatcher.group(1);
            if (!exteriorWalls.contains(wall)) {
                exteriorWalls.add(wall);
            }
        }

        // Phase 47: Cross-unit adjacency (party wall constraint)
        String adjacentUnit = null;
        Matcher adjacentUnitMatcher = ADJACENT_UNIT_PATTERN.matcher(roomContent);
        if (adjacentUnitMatcher.find()) {
            adjacentUnit = adjacentUnitMatcher.group(1);
        }

        // Phase 28: Unified DOOR pattern - handles both type:D1 and type:D1 size:900x2100
        // Size is optional - if missing, resolve from schedule
        Pattern doorUnifiedPattern = Pattern.compile(
            "DOOR\\s+type:(\\w+)(?:\\s+size:(\\d+)x(\\d+))?(?:\\s+wall:(north|south|east|west))?"
        );
        Matcher doorUnifiedMatcher = doorUnifiedPattern.matcher(roomContent);
        while (doorUnifiedMatcher.find()) {
            String doorTypeCode = doorUnifiedMatcher.group(1);
            double w = doorUnifiedMatcher.group(2) != null ? Integer.parseInt(doorUnifiedMatcher.group(2)) / 1000.0 : 0;
            double h = doorUnifiedMatcher.group(3) != null ? Integer.parseInt(doorUnifiedMatcher.group(3)) / 1000.0 : 0;
            String wall = doorUnifiedMatcher.group(4); // may be null
            if (wall == null && !exteriorWalls.isEmpty()) {
                wall = exteriorWalls.get(0); // default to first exterior wall
            }
            if (wall != null) {
                openings.add(new OpeningDef("DOOR", wall, null, w, h, doorTypeCode));
            }
        }

        // Phase 28: Unified WINDOW pattern - handles both type:W1 and type:W1 size:1800x1000
        // Size is optional - if missing, resolve from schedule
        Pattern winUnifiedPattern = Pattern.compile(
            "WINDOW\\s+type:(\\w+)(?:\\s+size:(\\d+)x(\\d+))?(?:\\s+wall:(north|south|east|west))?"
        );
        Matcher winUnifiedMatcher = winUnifiedPattern.matcher(roomContent);
        while (winUnifiedMatcher.find()) {
            String winTypeCode = winUnifiedMatcher.group(1);
            double w = winUnifiedMatcher.group(2) != null ? Integer.parseInt(winUnifiedMatcher.group(2)) / 1000.0 : 0;
            double h = winUnifiedMatcher.group(3) != null ? Integer.parseInt(winUnifiedMatcher.group(3)) / 1000.0 : 0;
            String wall = winUnifiedMatcher.group(4); // may be null
            if (wall == null && !exteriorWalls.isEmpty()) {
                wall = exteriorWalls.get(0); // default to first exterior wall
            }
            if (wall != null) {
                openings.add(new OpeningDef("WINDOW", wall, null, w, h, winTypeCode));
            }
        }

        return new RoomDef(finalRoomType, name, gridPos, width, depth, openings,
                          sprinklerSpacing, lightSpacing, adjacentTo, notAdjacentTo,
                          exteriorWall, alignsWith, above, below, stack, gridBounds, porchRoofType,
                          opensTo, zones, exteriorWalls, adjacentUnit);
    }

    /**
     * Extract content between matching braces starting after position.
     */
    private static String extractBlock(String content, int searchFrom) {
        int braceStart = content.indexOf('{', searchFrom);
        if (braceStart < 0) return "";

        int depth = 1;
        int i = braceStart + 1;
        while (i < content.length() && depth > 0) {
            char c = content.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') depth--;
            i++;
        }

        return content.substring(braceStart + 1, i - 1);
    }

    // =========================================================================
    // Test
    // =========================================================================

    public static void main(String[] args) {
        String dsl = """
            BUILDING "test_duplex" {
                STOREY "Ground" level:0 height:2.8m {
                    LIVING "main" at:A1 size:4x4m {
                        DOOR south
                        WINDOW east
                    }
                    STAIR "stair1" at:B1 width:1.0m to:"Upper"
                }
                STOREY "Upper" level:1 height:2.8m {
                    BEDROOM "bed1" at:A1 size:4x4m {
                        DOOR south to:landing
                        WINDOW east
                    }
                    LANDING "landing" at:B1 size:2x2m from:"stair1"
                }
                ROOF pitch:15deg
            }
            """;

        BuildingDefinition building = parse(dsl);

        System.out.println("Building: " + building.name());
        System.out.println("Storeys: " + building.storeys().size());

        for (StoreyDef storey : building.storeys()) {
            System.out.printf("\n  Storey: %s (level %d, height %.1fm)%n",
                storey.name(), storey.level(), storey.height());
            System.out.println("    Rooms: " + storey.rooms().size());
            for (RoomDef room : storey.rooms()) {
                System.out.printf("      %s \"%s\" at %s size %.1fx%.1fm%n",
                    room.type(), room.name(), room.gridPosition(), room.width(), room.depth());
                for (OpeningDef opening : room.openings()) {
                    System.out.printf("        %s %s%n", opening.type(), opening.wall());
                }
            }
            System.out.println("    Stairs: " + storey.stairs().size());
            for (StairDef stair : storey.stairs()) {
                System.out.printf("      %s at %s width %.1fm to %s%n",
                    stair.name(), stair.gridPosition(), stair.width(), stair.toStorey());
            }
            System.out.println("    Landings: " + storey.landings().size());
            for (LandingDef landing : storey.landings()) {
                System.out.printf("      %s at %s size %.1fx%.1fm from %s%n",
                    landing.name(), landing.gridPosition(), landing.width(), landing.depth(), landing.fromStair());
            }
        }

        if (building.roof() != null) {
            System.out.printf("\nRoof: %.0f deg pitch%n", building.roof().pitchDegrees());
        }

        System.out.println("\n[PASS] Parser test complete");
    }
}
