package com.aman.AirBnb.AirBnb.Controller;

import com.aman.AirBnb.AirBnb.Dto.RoomDTO;
import com.aman.AirBnb.AirBnb.Service.Interfaces.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/hotels/{hotelId}/rooms")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Room Admin Management", description = "Admin Manage rooms/hotels/inventories in a hotel")
public class RoomAdminController {
    private final RoomService roomService;

    @PostMapping
    @Operation(summary = "Create a new room",
            description = "Adds a new room to a specific hotel")
    public ResponseEntity<RoomDTO> createNewRoom(@PathVariable Long hotelId,
                                                 @RequestBody RoomDTO roomDto) {
        RoomDTO room = roomService.createNewRoom(hotelId, roomDto);
        return new ResponseEntity<>(room, HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Retrieve all rooms in a hotel",
            description = "Fetches all rooms belonging to the specified hotel")
    public ResponseEntity<List<RoomDTO>> getAllRoomsInHotel(@PathVariable Long hotelId) {
        return ResponseEntity.ok(roomService.getAllRoomsInHotel(hotelId));
    }

    @GetMapping("/{roomId}")
    @Operation(summary = "Get details of a specific room",
            description = "Fetches details of a specific room in a hotel by ID")
    public ResponseEntity<RoomDTO> getRoomById(@PathVariable Long hotelId,
                                               @PathVariable Long roomId) {
        return ResponseEntity.ok(roomService.getRoomById(roomId));
    }

    @DeleteMapping("/{roomId}")
    @Operation(summary = "Delete a room",
            description = "Deletes a room from the hotel by ID")
    public ResponseEntity<Void> deleteRoomById(@PathVariable Long hotelId,
                                               @PathVariable Long roomId) {
        roomService.deleteRoomById(roomId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{roomId}")
    @Operation(summary = "Update a room",
            description = "Updates the details of an existing room")
    public ResponseEntity<RoomDTO> updateRoomById(@PathVariable Long hotelId,
                                                  @PathVariable Long roomId,
                                                  @RequestBody RoomDTO roomDto) {
        return ResponseEntity.ok(roomService.updateRoomById(hotelId, roomId, roomDto));
    }

    // ✅ NEW
    @PatchMapping("/{roomId}/deactivate")
    @Operation(summary = "Deactivate a room",
            description = "Marks a room as inactive so it no longer appears for bookings")
    public ResponseEntity<Void> deactivateRoom(@PathVariable Long hotelId,
                                               @PathVariable Long roomId) {
        roomService.deactivateRoom(hotelId, roomId);
        return ResponseEntity.noContent().build();
    }

    // ✅ NEW
    @PatchMapping("/{roomId}/activate")
    @Operation(summary = "Activate a room",
            description = "Marks a room as active so it appears for bookings again")
    public ResponseEntity<Void> activateRoom(@PathVariable Long hotelId,
                                             @PathVariable Long roomId) {
        roomService.activateRoom(hotelId, roomId);
        return ResponseEntity.noContent().build();
    }
}