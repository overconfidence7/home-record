package com.kurtz.homerecord.core.data

import com.kurtz.homerecord.core.model.Home
import com.kurtz.homerecord.core.model.Room
import com.kurtz.homerecord.core.model.Wall
import com.kurtz.homerecord.core.model.Panel
import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.BreakerRoom
import com.kurtz.homerecord.core.model.Endpoint
import com.kurtz.homerecord.core.model.Shutoff
import com.kurtz.homerecord.core.model.Equipment
import com.kurtz.homerecord.core.model.Photo
import com.kurtz.homerecord.core.model.Job
import com.kurtz.homerecord.core.model.LogEntry
import com.kurtz.homerecord.core.model.DailyReport
import com.kurtz.homerecord.core.model.CompanyProfile
import com.kurtz.homerecord.core.model.LoadProfile
import com.kurtz.homerecord.core.model.MaintenanceTask
import com.kurtz.homerecord.core.logic.HvacKind
import com.kurtz.homerecord.core.logic.LoadProfileCodec

fun HomeEntity.toDomain() = Home(
    id = id,
    name = name,
    address = address,
    yearBuilt = yearBuilt,
    climateZone = climateZone,
    createdAt = createdAt,
    updatedAt = updatedAt,
    source = source,
    squareFeet = squareFeet,
)

fun Home.toEntity() = HomeEntity(
    id = id,
    name = name,
    address = address,
    yearBuilt = yearBuilt,
    climateZone = climateZone,
    createdAt = createdAt,
    updatedAt = updatedAt,
    source = source,
    squareFeet = squareFeet,
)

fun RoomEntity.toDomain() = Room(
    id = id,
    homeId = homeId,
    name = name,
    floor = floor,
    createdAt = createdAt,
    updatedAt = updatedAt,
    source = source,
    kind = kind,
)

fun Room.toEntity() = RoomEntity(
    id = id,
    homeId = homeId,
    name = name,
    floor = floor,
    createdAt = createdAt,
    updatedAt = updatedAt,
    source = source,
    kind = kind,
)

fun WallEntity.toDomain() = Wall(
    id = id,
    roomId = roomId,
    label = label,
    createdAt = createdAt,
    updatedAt = updatedAt,
    source = source,
)

fun Wall.toEntity() = WallEntity(
    id = id,
    roomId = roomId,
    label = label,
    createdAt = createdAt,
    updatedAt = updatedAt,
    source = source,
)

fun PanelEntity.toDomain() = Panel(
    id = id,
    homeId = homeId,
    name = name,
    locationRoomId = locationRoomId,
    photoPath = photoPath,
    slotCount = slotCount,
    layout = layout,
    createdAt = createdAt,
    updatedAt = updatedAt,
    source = source,
    kind = kind,
    ratingAmps = ratingAmps,
    brand = brand,
    model = model,
    maxCircuits = maxCircuits,
    tandemPolicy = tandemPolicy,
    hasMainBreaker = hasMainBreaker,
    yearInstalled = yearInstalled,
    feedBreakerId = feedBreakerId,
)

fun Panel.toEntity() = PanelEntity(
    id = id,
    homeId = homeId,
    name = name,
    locationRoomId = locationRoomId,
    photoPath = photoPath,
    slotCount = slotCount,
    layout = layout,
    createdAt = createdAt,
    updatedAt = updatedAt,
    source = source,
    kind = kind,
    ratingAmps = ratingAmps,
    brand = brand,
    model = model,
    maxCircuits = maxCircuits,
    tandemPolicy = tandemPolicy,
    hasMainBreaker = hasMainBreaker,
    yearInstalled = yearInstalled,
    feedBreakerId = feedBreakerId,
)

fun BreakerEntity.toDomain() = Breaker(
    id = id,
    panelId = panelId,
    slots = slots,
    poles = poles,
    amps = amps,
    type = type,
    label = label,
    confidence = confidence,
    tandemIndex = tandemIndex,
    createdAt = createdAt,
    updatedAt = updatedAt,
    source = source,
    isMain = isMain,
)

fun Breaker.toEntity() = BreakerEntity(
    id = id,
    panelId = panelId,
    slots = slots,
    poles = poles,
    amps = amps,
    type = type,
    label = label,
    confidence = confidence,
    tandemIndex = tandemIndex,
    createdAt = createdAt,
    updatedAt = updatedAt,
    source = source,
    isMain = isMain,
)

fun BreakerRoomEntity.toDomain() = BreakerRoom(
    id = id,
    breakerId = breakerId,
    roomId = roomId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    source = source,
)

fun BreakerRoom.toEntity() = BreakerRoomEntity(
    id = id,
    breakerId = breakerId,
    roomId = roomId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    source = source,
)

fun EndpointEntity.toDomain() = Endpoint(
    id = id,
    breakerId = breakerId,
    roomId = roomId,
    kind = kind,
    label = label,
    createdAt = createdAt,
    updatedAt = updatedAt,
    source = source,
)

fun Endpoint.toEntity() = EndpointEntity(
    id = id,
    breakerId = breakerId,
    roomId = roomId,
    kind = kind,
    label = label,
    createdAt = createdAt,
    updatedAt = updatedAt,
    source = source,
)

fun ShutoffEntity.toDomain() = Shutoff(
    id = id,
    homeId = homeId,
    kind = kind,
    roomId = roomId,
    photoPath = photoPath,
    instructions = instructions,
    createdAt = createdAt,
    updatedAt = updatedAt,
    source = source,
)

fun Shutoff.toEntity() = ShutoffEntity(
    id = id,
    homeId = homeId,
    kind = kind,
    roomId = roomId,
    photoPath = photoPath,
    instructions = instructions,
    createdAt = createdAt,
    updatedAt = updatedAt,
    source = source,
)

fun EquipmentEntity.toDomain() = Equipment(
    id = id,
    homeId = homeId,
    roomId = roomId,
    category = category,
    make = make,
    model = model,
    serial = serial,
    manufacturedOn = manufacturedOn,
    installedOn = installedOn,
    warrantyEnds = warrantyEnds,
    labelPhotoPath = labelPhotoPath,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    source = source,
)

fun Equipment.toEntity() = EquipmentEntity(
    id = id,
    homeId = homeId,
    roomId = roomId,
    category = category,
    make = make,
    model = model,
    serial = serial,
    manufacturedOn = manufacturedOn,
    installedOn = installedOn,
    warrantyEnds = warrantyEnds,
    labelPhotoPath = labelPhotoPath,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    source = source,
)

fun PhotoEntity.toDomain() = Photo(
    id = id,
    homeId = homeId,
    roomId = roomId,
    wallId = wallId,
    stage = stage,
    takenAt = takenAt,
    caption = caption,
    path = path,
    createdAt = createdAt,
    updatedAt = updatedAt,
    source = source,
)

fun Photo.toEntity() = PhotoEntity(
    id = id,
    homeId = homeId,
    roomId = roomId,
    wallId = wallId,
    stage = stage,
    takenAt = takenAt,
    caption = caption,
    path = path,
    createdAt = createdAt,
    updatedAt = updatedAt,
    source = source,
)

fun JobEntity.toDomain() = Job(
    id = id, homeId = homeId, name = name, address = address, clientName = clientName,
    clientContact = clientContact, archived = archived, createdAt = createdAt, updatedAt = updatedAt, source = source,
)

fun Job.toEntity() = JobEntity(
    id = id, homeId = homeId, name = name, address = address, clientName = clientName,
    clientContact = clientContact, archived = archived, createdAt = createdAt, updatedAt = updatedAt, source = source,
)

fun LogEntryEntity.toDomain() = LogEntry(
    id = id, jobId = jobId, day = day, kind = kind, text = text, mediaPath = mediaPath,
    durationMs = durationMs, roomId = roomId, createdAt = createdAt, updatedAt = updatedAt, source = source,
)

fun LogEntry.toEntity() = LogEntryEntity(
    id = id, jobId = jobId, day = day, kind = kind, text = text, mediaPath = mediaPath,
    durationMs = durationMs, roomId = roomId, createdAt = createdAt, updatedAt = updatedAt, source = source,
)

fun DailyReportEntity.toDomain() = DailyReport(
    id = id, jobId = jobId, day = day, weather = weather, crewCount = crewCount, hours = hours,
    workCompleted = workCompleted, delays = delays, delayReason = delayReason, materials = materials,
    nextSteps = nextSteps, pdfPath = pdfPath, createdAt = createdAt, updatedAt = updatedAt, source = source,
)

fun DailyReport.toEntity() = DailyReportEntity(
    id = id, jobId = jobId, day = day, weather = weather, crewCount = crewCount, hours = hours,
    workCompleted = workCompleted, delays = delays, delayReason = delayReason, materials = materials,
    nextSteps = nextSteps, pdfPath = pdfPath, createdAt = createdAt, updatedAt = updatedAt, source = source,
)

fun CompanyProfileEntity.toDomain() = CompanyProfile(
    id = id, name = name, phone = phone, email = email, logoPath = logoPath,
    createdAt = createdAt, updatedAt = updatedAt, source = source,
)

fun CompanyProfile.toEntity() = CompanyProfileEntity(
    id = id, name = name, phone = phone, email = email, logoPath = logoPath,
    createdAt = createdAt, updatedAt = updatedAt, source = source,
)

fun LoadProfileEntity.toDomain() = LoadProfile(
    id = id, homeId = homeId, appliances = LoadProfileCodec.decode(appliances),
    hvac = runCatching { HvacKind.valueOf(hvac) }.getOrDefault(HvacKind.NONE),
    hvacVa = hvacVa, stripVa = stripVa, createdAt = createdAt, updatedAt = updatedAt, source = source,
)

fun LoadProfile.toEntity() = LoadProfileEntity(
    id = id, homeId = homeId, appliances = LoadProfileCodec.encode(appliances), hvac = hvac.name,
    hvacVa = hvacVa, stripVa = stripVa, createdAt = createdAt, updatedAt = updatedAt, source = source,
)

fun MaintenanceTaskEntity.toDomain() = MaintenanceTask(
    id = id, homeId = homeId, equipmentId = equipmentId, title = title, rule = rule,
    intervalMonths = intervalMonths, season = season, lastDone = lastDone, nextDue = nextDue,
    createdAt = createdAt, updatedAt = updatedAt, source = source,
)

fun MaintenanceTask.toEntity() = MaintenanceTaskEntity(
    id = id, homeId = homeId, equipmentId = equipmentId, title = title, rule = rule,
    intervalMonths = intervalMonths, season = season, lastDone = lastDone, nextDue = nextDue,
    createdAt = createdAt, updatedAt = updatedAt, source = source,
)
