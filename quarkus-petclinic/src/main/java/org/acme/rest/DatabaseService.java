package org.acme.rest;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

import javax.naming.NamingException;
import javax.sql.DataSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

/**
 * 
 */
@ApplicationScoped
public class DatabaseService {

  @Inject
  DataSource dataSource; // Iniettare il datasource configurato

  @Inject
  EntityManager entityManager;

  // TEST methods
  // ---------------------------------------------------------------------------------------
  @Transactional
  public String getDatabaseName() {
    // Query SQL per ottenere il nome del database
    String databaseName = (String) entityManager.createNativeQuery("SELECT DATABASE()").getSingleResult();
    return databaseName;
  }

  /**
   * returns all tables of DB
   * 
   * @return
   */
  @Transactional
  public String listTables() {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {

      StringBuilder stringBuilder = new StringBuilder();
      // Modifica questa query in base al database che stai usando
      String query = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC';";
      ResultSet rs = stmt.executeQuery(query);

      System.out.println("Tabelle nel database:");
      while (rs.next()) {
        String tableName = rs.getString("TABLE_NAME");
        stringBuilder.append(tableName);
        stringBuilder.append("\n");
      }

      return stringBuilder.toString();

    } catch (Exception e) {
      e.printStackTrace();
      return "error";
    }
  }

  /**
   * Test Method that retuns all instanced of the database
   * 
   * @return
   */
  public String getAllInstances() {
    StringBuilder result = new StringBuilder();

    String[] tables = { "vets", "specialties", "vet_specialties", "types", "owners", "pets", "visits" };

    try (Connection conn = dataSource.getConnection()) {
      if (conn == null) {
        return "Connection failed"; // Aggiunto log di errore
      }

      for (String table : tables) {
        result.append("Records from table: ").append(table).append("\n");

        try (Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM " + table)) {

          // Controlla se ci sono risultati
          if (!rs.isBeforeFirst()) {
            result.append("No records found.\n");
          } else {
            while (rs.next()) {
              StringBuilder row = new StringBuilder();
              int columnCount = rs.getMetaData().getColumnCount();

              for (int i = 1; i <= columnCount; i++) {
                row.append(rs.getMetaData().getColumnName(i)).append(": ")
                    .append(rs.getString(i)).append(", ");
              }
              result.append(row.toString()).append("\n");
            }
          }
        }
      }

    } catch (Exception e) {
      e.printStackTrace();
      return "Error: " + e.getMessage();
    }

    return result.toString();
  }

  // NOTE: Owner methods
  // ---------------------------------------------------------------------------------------

  /**
   * @param ownerId
   * @return owner with that id if exits or null if it doesn't
   * @throws SQLException
   */
  public Owner getOwner(long ownerId) throws SQLException {
    String query = "SELECT * FROM owners WHERE id = ?";
    String query_pets = "SELECT id FROM pets WHERE id = ? ";
    Set<Pet> pets = new HashSet<>();
    Owner owner = new Owner();
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(query);
        PreparedStatement stmt_pets = conn.prepareStatement(query_pets)) {

      stmt.setLong(1, ownerId);
      ResultSet rs = stmt.executeQuery();
      if (!rs.next()) {
        return null;
      }
      owner.setId(rs.getInt("id"));
      owner.setFirstName(rs.getString("first_name"));
      owner.setLastName(rs.getString("last_name"));
      owner.setCity(rs.getString("city"));
      owner.setAddress(rs.getString("address"));
      owner.setTelephone(rs.getString("telephone"));

      // we set his pets
      stmt_pets.setLong(1, ownerId);
      ResultSet rs_pets = stmt_pets.executeQuery();
      while (rs_pets.next()) {
        pets.add(getPet(rs_pets.getLong("id")));
      }

      owner.setPets(pets);

    } catch (SQLException e) {
      e.printStackTrace();
      throw new SQLException();
    }
    return owner;
  }

  /**
   * @param ownerId
   * @param petId
   * @return Pet data if that pet is related to that owner
   * @throws SQLException
   * @throws NotFoundException
   */
  public Pet getOwnerPet(long ownerId, long petId) throws SQLException, NotFoundException {

    Pet pet = getPet(petId);
    Owner owner = getOwner(ownerId);
    if (pet == null) {
      throw new NotFoundException("Pet not found");
    }
    if (owner == null) {
      throw new NotFoundException("Owner not found");
    }
    if (pet.getOwner().getId() != owner.getId()) {
      throw new NotFoundException("Not that owner's pet");
    }
    return pet;

  }

  /**
   * adds a new owner
   * 
   * @param owner
   * @throws SQLException
   * @throws IllegalArgumentException
   */
  public void addOwner(Owner owner) throws SQLException, IllegalArgumentException {

    if (getOwnerByLastName(owner.getLastName()) != null) {
      throw new IllegalArgumentException();
    }
    String query = "INSERT INTO owners (first_name, last_name, address, city, telephone) VALUES (?, ?, ?, ?, ?)";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
      if (owner.getFirstName() == null || owner.getFirstName().isEmpty() ||
          owner.getLastName() == null || owner.getLastName().isEmpty() ||
          owner.getAddress() == null || owner.getAddress().isEmpty() ||
          owner.getCity() == null || owner.getCity().isEmpty() ||
          owner.getTelephone() == null || owner.getTelephone().isEmpty()) {

        throw new IllegalArgumentException("All fields of the owner must be filled.");
      }
      stmt.setString(1, owner.getFirstName());
      stmt.setString(2, owner.getLastName());
      stmt.setString(3, owner.getAddress());
      stmt.setString(4, owner.getCity());
      stmt.setString(5, owner.getTelephone());
      stmt.executeUpdate();

      try (ResultSet rs_keys = stmt.getGeneratedKeys()) {
        rs_keys.next();
        owner.setId(rs_keys.getLong(1));
      }
    } catch (SQLException e) {
      e.printStackTrace();
      throw new SQLException();
    }
  }

  /**
   * returns the Owner given the lastname
   * 
   * @param lastName
   * @return
   * @throws SQLException
   */
  private Owner getOwnerByLastName(String lastName) throws SQLException {
    Owner owner = new Owner();
    System.out.println("lastName::::::::" + lastName);
    String query = "SELECT id FROM owners WHERE last_name LIKE ?";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement preparedStatement = connection.prepareStatement(query);) {
      preparedStatement.setString(1, lastName);
      ResultSet rs = preparedStatement.executeQuery();
      if (!rs.next()) {
        return null;
      }
      owner = getOwner(rs.getLong("id"));
      return owner;

    } catch (SQLException e) {
      e.printStackTrace();
      throw new SQLException();
    }

  }

  /**
   * Returns the owner with that lastname with his pet List
   * 
   * @param lastName
   * @return Owner
   * @throws SQLException
   * @throws NotFoundException
   */
  public Owner listOwnerPets(String lastName) throws SQLException, NotFoundException {
    Owner owner = getOwnerByLastName(lastName);
    if (owner == null) {
      System.out.println("Owner::::" + owner);
      throw new NotFoundException("Owner not found");
    }
    String query = "SELECT id FROM pets WHERE owner_id = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(query)) {

      stmt.setLong(1, owner.getId());
      ResultSet rs = stmt.executeQuery();
      Set<Pet> pets = new HashSet<>();
      while (rs.next()) {

        Pet pet = getPet(rs.getLong("id"));
        pets.add(pet);

      }

      owner.setPets(pets);
      return owner;
    } catch (SQLException e) {
      e.printStackTrace();
      throw new SQLException();
    }
  }

  /**
   * update Owner details
   * 
   * @param ownerId
   * @param owner
   * @return the owners with the id
   * @throws SQLException
   * @throws NotFoundException
   * @throws IllegalArgumentException
   */
  public Owner updateOwner(long ownerId, Owner owner) throws SQLException, NotFoundException, IllegalArgumentException {

    String query = "UPDATE owners SET first_name = ?, last_name = ?, address = ?, city = ?, telephone = ? WHERE id = ?";
    try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(query)) {

      if (getOwner(ownerId) == null) {
        throw new NotFoundException();
      }

      if (owner.getFirstName() == null || owner.getFirstName().isEmpty() ||
          owner.getLastName() == null || owner.getLastName().isEmpty() ||
          owner.getAddress() == null || owner.getAddress().isEmpty() ||
          owner.getCity() == null || owner.getCity().isEmpty() ||
          owner.getTelephone() == null || owner.getTelephone().isEmpty()) {

        throw new IllegalArgumentException("All fields of the owner must be filled.");
      }

      stmt.setString(1, owner.getFirstName());
      stmt.setString(2, owner.getLastName());
      stmt.setString(3, owner.getAddress());
      stmt.setString(4, owner.getCity());
      stmt.setString(5, owner.getTelephone());
      stmt.setLong(6, ownerId);
      stmt.executeUpdate();

      owner.setId(ownerId);
      return owner;

    } catch (SQLException e) {
      e.printStackTrace();
      throw new SQLException();
    }
  }

  /**
   * Delete the owner with that id
   * 
   * @param ownerId
   * @return the owner deleted, null if has a relation
   * @throws NotFoundException
   * @throws SQLException
   */
  public Owner deleteOwner(int ownerId) throws NotFoundException, SQLException, ObjectReferenceException {

    Owner owner = getOwner(ownerId);
    if (owner == null) {
      throw new NotFoundException("Owner not found");
    }

    String query = "DELETE FROM owners WHERE id = ?";
    try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(query)) {
      stmt.setInt(1, ownerId);
      stmt.executeUpdate();

      return owner;
    } catch (SQLException e) {
      e.printStackTrace();
      if (e.getSQLState().equals("23503")) {
        throw new ObjectReferenceException();

      }

      throw new SQLException();

    }
  }

  public void addPetToOwner(long ownerId, Pet pet) throws SQLException, NotFoundException {
    String query = "UPDATE pets SET owner_id=? WHERE id=?";
    System.out.println("------------------------------------");
    try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(query)) {
      stmt.setLong(1, ownerId);
      Pet petCaughtByName = getPetByName(pet.getName());
      pet.setId(petCaughtByName.getId());
      stmt.setLong(2, petCaughtByName.getId());
      int affected_rows = stmt.executeUpdate();
      if (affected_rows == 0) {
        throw new NotFoundException("Owner or Pet not found");
      }

    } catch (SQLException e) {
      e.printStackTrace();
      throw new SQLException(e.getMessage());
    } catch (NotFoundException e) {
      e.printStackTrace();
      throw new NotFoundException(e.getMessage());
    }
  }

  // NOTE: PET methods
  // ----------------------------------------------------------------------------------------------

  private Pet getPetByName(String name) throws SQLException, NotFoundException {
    Pet pet = new Pet();
    String query = "SELECT * FROM pets WHERE name = ?";

    try (Connection connection = dataSource.getConnection();
        PreparedStatement stmt = connection.prepareStatement(query);) {
      stmt.setString(1, name);
      ResultSet rs = stmt.executeQuery();
      if (!rs.next()) {
        throw new NotFoundException("Pet not found from name");
      }

      pet.setId(rs.getLong("id"));
      pet.setName(name);
      pet.setBirthDate(LocalDate.parse(rs.getDate("birth_date").toString()));
      pet.setType(getType(rs.getLong("type_id")));
      long ownerId = rs.getLong("owner_id");
      if (ownerId != 0) {
        pet.setOwner(getOwner(ownerId));
      }

    } catch (SQLException e) {
      e.printStackTrace();
      throw new SQLException();
    }
    return pet;

  }

  /**
   * @param petId
   * @return the Pet Found
   * @throws NotFoundException
   */
  public Pet getPet(long petId) throws NotFoundException {
    Set<Visit> visits = new HashSet<>();
    String query_visits = "SELECT id FROM visits WHERE pet_id = ?";
    String query = "SELECT * FROM pets WHERE id = ?";
    Pet pet = null;
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(query);
        PreparedStatement stmt_visits = conn.prepareStatement(query_visits)) {
      stmt.setLong(1, petId);
      ResultSet rs = stmt.executeQuery();
      if (!rs.next()) {
        throw new NotFoundException("Pet not found");
      }
      pet = new Pet();
      pet.setId(rs.getInt("id"));
      pet.setName(rs.getString("name"));
      pet.setBirthDate(rs.getDate("birth_date").toLocalDate());
      // set references
      System.out.println("#################");
      System.out.println(rs.getLong("owner_id"));
      long ownerIdReference = rs.getLong("owner_id");
      if (ownerIdReference != 0) {
        pet.setOwnerId(ownerIdReference);
      }

      stmt_visits.setLong(1, petId);
      ResultSet rs_visits = stmt_visits.executeQuery();
      while (rs_visits.next()) {

        visits.add(getVisit(rs.getLong("id")));
      }

      pet.setVisits(visits);

      pet.setType(getType(rs.getLong("type_id")));
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return pet;
  }

  public List<Pet> listPets() throws SQLException {
    String query = "SELECT id FROM pets";
    List<Pet> pets = new ArrayList<>();
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query)) {
      while (rs.next()) {
        Pet pet = getPet(rs.getLong("id"));
        pets.add(pet);
      }
    } catch (SQLException e) {
      e.printStackTrace();
      throw new SQLException();
    }
    return pets;
  }

  /**
   * @param pet
   *            add a new Pet if the Type exists
   * @exception BadRequestException if type do not exists or its id does not match
   */
  public void addPet(Pet pet) throws SQLException {

    String query = "INSERT INTO pets (name, birth_date,type_id) VALUES (?,?,?)";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {

      Type type_from_request = pet.getType();
      System.out.println(type_from_request.getId() + ": TYPE ID");

      Type type = getType(type_from_request.getId());
      System.out.println(type);
      System.out.println(type_from_request.getName() + " == " + type.getName() + "?");
      if (type == null || !type.getName().equals(type_from_request.getName())) {
        System.out.println("bad request" + "\n" + type.toString());
        throw new BadRequestException();
      }
      System.out.println("check passed");
      stmt.setString(1, pet.getName());
      stmt.setDate(2, Date.valueOf(pet.getBirthDate()));
      stmt.setLong(3, pet.getType().getId());
      stmt.executeUpdate();
      try (ResultSet keys = stmt.getGeneratedKeys()) {
        if (!keys.next()) {
          throw new SQLException("No key");
        }
        Long key = keys.getLong(1);
        pet.setId(key.intValue());

      }

    } catch (SQLException e) {
      e.printStackTrace();
      throw new SQLException();
    }

  }

  /**
   * update a pet with that id
   * 
   * @param petId
   * @param pet
   * @throws NotFoundException
   * @throws SQLException
   */
  public void updatePet(long petId, Pet pet) throws NotFoundException, SQLException {
    if (getPet(petId) == null) {
      throw new NotFoundException();

    }

    String query = "UPDATE pets SET name = ?, birth_date = ?, type_id = ? WHERE id = ?";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement stmt = connection.prepareStatement(query)) {
      stmt.setString(1, pet.getName());
      stmt.setDate(2, Date.valueOf(pet.getBirthDate()));
      // WARNING: TYPE id and name mismatch not checked
      stmt.setLong(3, pet.getType().getId());
      stmt.setLong(4, petId);

      stmt.executeUpdate();

    } catch (SQLException e) {
      e.printStackTrace();
      throw new SQLException();
    }

  }

  /**
   * @param petId
   * @return
   * @throws NotFoundException
   * @throws SQLException
   */
  public Pet deletePet(long petId) throws NotFoundException, SQLException {

    Pet pet = getPet(petId);

    String query_delete = "DELETE FROM pets WHERE id=?";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement stmt_delete = connection.prepareStatement(query_delete);) {

      stmt_delete.setLong(1, petId);
      stmt_delete.executeUpdate();

    } catch (SQLException e) {
      e.printStackTrace();
      throw new SQLException();
    }

    return pet;

  }

  // NOTE: Types Methods
  // -----------------------------------------------------------------------------------------------

  /**
   * @param id
   * @return Type if found, null if not found
   */
  public Type getType(long id) throws SQLException {

    Type type = new Type();

    String query = "SELECT * FROM types WHERE id=?";

    try (Connection connection = dataSource.getConnection();
        PreparedStatement stmt = connection.prepareStatement(query)) {
      stmt.setLong(1, id);
      ResultSet rs = stmt.executeQuery();
      if (!rs.next()) {
        System.out.println("type not found from query where id:" + id);
        return null;
      } else {
        System.out.println("type found:" + rs.getString("name"));
      }

      type.setId(id);
      type.setName(rs.getString("name"));

      return type;
    } catch (SQLException e) {
      e.printStackTrace();
      throw new SQLException();
    }

  }

  /**
   * @param name
   * @return Type if exists with that name
   * @throws SQLException
   */
  private Type getTypeByName(String name) throws SQLException {

    String query = "SELECT id FROM types WHERE name = ? ";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(query)) {
      stmt.setString(1, name);
      ResultSet rs = stmt.executeQuery();
      if (rs.next()) {
        Type type = getType(rs.getLong("id"));
        return type;
      }
      return null;
    } catch (SQLException e) {
      e.printStackTrace();
      throw new SQLException();
    }
  }

  /**
   * @param name
   * @return true if is unique, false otherways
   * @throws SQLException
   */
  private boolean pettypesIsUnique(String name) throws SQLException {

    String query = "SELECT COUNT(*) AS instances FROM types WHERE name = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(query)) {
      System.out.println("problem 1");
      stmt.setString(1, name);
      System.out.println("problem 2");
      ResultSet rs = stmt.executeQuery();
      if (rs.next()) {
        int number_of_instances = rs.getInt("instances");
        System.out.println("problem 3");
        return (number_of_instances == 0);
      }
      return false;
    } catch (SQLException e) {
      e.printStackTrace();
      throw new SQLException();
    }

  }

  /**
   * adds a new pettype
   * 
   * @param typeName
   */
  public void addPetType(Type pettype) throws SQLException, NamingException {

    System.out.println("In pettype creation");
    if (!pettypesIsUnique(pettype.getName())) {
      System.out.println("name not unique");
      throw new NamingException();
    }

    String query = "INSERT INTO types (name) VALUES (?)";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
      System.out.println("problem 4");

      stmt.setString(1, pettype.getName());

      System.out.println("problem 5");

      stmt.executeUpdate();
      try (ResultSet keys = stmt.getGeneratedKeys()) {
        if (!keys.next()) {
          throw new SQLException("No key");
        }
        Long key = keys.getLong(1);
        pettype.setId(key.intValue());

        System.out.println("pet created");

      } catch (SQLException e) {
        e.printStackTrace();
        throw new SQLException();
      }
    }
  }

  /**
   * update the given pettype
   * 
   * @param type
   * @return
   * @throws NotFoundException
   * @throws SQLException
   * @throws NamingException
   */
  public Type updatePetType(Type type) throws NotFoundException, SQLException, NamingException {
    Type storedType = getType(type.getId());
    if (storedType == null) {
      throw new NotFoundException();
    }
    if (type.getName() == null || type.getName() == "") {
      throw new NamingException();
    }
    String query = " UPDATE types SET name = ? WHERE id = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(query);) {
      stmt.setString(1, type.getName());
      stmt.setLong(2, storedType.getId());

      type.setId(storedType.getId());
      return type;
    } catch (SQLException e) {
      e.printStackTrace();
      throw new SQLException();
    }

  }

  /**
   * @return a Set of pettypes
   * @throws SQLException
   */
  public Set<Type> listPetTypes() throws SQLException {
    String query = "SELECT id FROM types";
    Set<Type> types = new HashSet<>();
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query)) {
      while (rs.next()) {
        long type_id = rs.getInt("id");
        Type type = getType(type_id);

        types.add(type);
      }
    } catch (SQLException e) {
      e.printStackTrace();
      throw new SQLException();
    }
    return types;
  }

  /**
   * delete pettype
   * 
   * @param pettypeId
   * @return
   * @throws NotFoundException
   * @throws SQLException
   * @throws ObjectReferenceException
   */
  public Type deletePetType(long pettypeId) throws NotFoundException, SQLException, ObjectReferenceException {

    Type type = getType(pettypeId);
    if (type == null) {
      throw new NotFoundException("type not found");
    }

    String query = "DELETE FROM types WHERE id = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(query)) {
      stmt.setLong(1, pettypeId);
      stmt.executeUpdate();

      return type;

    } catch (SQLException e) {
      e.printStackTrace();
      if (e.getSQLState().equals("23503")) {
        throw new ObjectReferenceException();
      }
      throw new SQLException();
    }
  }

  // NOTE: Specialty methods
  // ----------------------------------------------------------------------------------------------

  /**
   * @param specialtyId
   * @return a Specialty given an id
   * @throws SQLException
   */
  public Specialty getSpecialty(long specialtyId) throws SQLException {

    String query = "SELECT * FROM specialties WHERE id = ?";
    try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(query)) {
      stmt.setLong(1, specialtyId);

      ResultSet rs = stmt.executeQuery();

      Specialty specialty;
      if (!rs.next()) {
        return null;
      }
      specialty = new Specialty();
      specialty.setId(rs.getLong("id"));
      specialty.setName(rs.getString("name"));

      return specialty;
    } catch (SQLException e) {
      e.printStackTrace();
      throw new SQLException();
    }
  }

  /**
   * @param name
   * @return specialty with that name if exists, otherways null
   * @throws SQLException
   */
  public Specialty getSpecialtyByName(String name) throws SQLException {
    String query = "SELECT id FROM specialties WHERE name = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(query)) {
      stmt.setString(1, name);
      ResultSet rs = stmt.executeQuery();

      if (!rs.next()) {
        return null;

      }

      Specialty specialty = getSpecialty(rs.getLong("id"));
      return specialty;
    } catch (SQLException e) {
      e.printStackTrace();
      throw new SQLException();
    }
  }

  /**
   * add a new Specialty
   * 
   * @param specialtyName
   */
  public void addSpecialty(String specialtyName) {
    String query = "INSERT INTO specialties (name) VALUES (?)";
    try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(query)) {
      stmt.setString(1, specialtyName);
      stmt.executeUpdate();
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  /**
   * @return a set of Specialtyes
   * @throws SQLException
   */
  public Set<Specialty> listSpecialties() throws SQLException {
    String query = "SELECT id FROM specialties";
    Set<Specialty> specialties = new HashSet<>();
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query)) {
      while (rs.next()) {
        Specialty specialty = getSpecialty(rs.getLong("id"));
        specialties.add(specialty);
      }

      return specialties;
    } catch (SQLException e) {
      e.printStackTrace();
      throw new SQLException();
    }
  }

  /**
   * @param name
   * @return true if is unique, false otherways
   * @throws SQLException
   */
  private boolean specialtyIsUnique(String name) throws SQLException {

    String query = "SELECT COUNT(*) AS instances FROM specialties WHERE name = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(query)) {
      stmt.setString(1, name);
      ResultSet rs = stmt.executeQuery();
      if (rs.next()) {
        int number_of_instances = rs.getInt("instances");
        return (number_of_instances == 0);
      }
      return false;
    } catch (SQLException e) {
      e.printStackTrace();
      throw new SQLException();
    }

  }

  /**
   * adds a new specialty
   * 
   * @param typeName
   */
  public void addSpecialty(Specialty specialty) throws SQLException, NamingException {

    if (!specialtyIsUnique(specialty.getName())) {
      System.out.println("name not unique");
      throw new NamingException();
    }

    String query = "INSERT INTO specialties (name) VALUES (?)";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {

      stmt.setString(1, specialty.getName());

      stmt.executeUpdate();
      try (ResultSet keys = stmt.getGeneratedKeys()) {
        if (!keys.next()) {
          throw new SQLException("No key");
        }
        Long key = keys.getLong(1);
        specialty.setId(key.intValue());

      } catch (SQLException e) {
        e.printStackTrace();
        throw new SQLException();
      }
    }
  }

  /**
   * update the given specialty
   * 
   * @param type
   * @return
   * @throws NotFoundException
   * @throws SQLException
   * @throws NamingException
   */
  public Specialty updateSpecialty(Specialty specialty) throws NotFoundException, SQLException, NamingException {
    Specialty storedSpecialty = getSpecialty(specialty.getId());
    if (storedSpecialty == null) {
      throw new NotFoundException();
    }
    if (specialty.getName() == null || specialty.getName() == "") {
      throw new NamingException();
    }
    String query = "UPDATE specialties SET name = ? WHERE id = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(query);) {
      stmt.setString(1, specialty.getName());
      stmt.setLong(2, storedSpecialty.getId());

      specialty.setId(storedSpecialty.getId());
      return specialty;
    } catch (SQLException e) {
      e.printStackTrace();
      throw new SQLException();
    }

  }

  /**
   * delete specialty
   * 
   * @param specialtyId
   * @return
   * @throws NotFoundException
   * @throws SQLException
   * @throws ObjectReferenceException
   */
  public Specialty deleteSpecialty(long specialtyId) throws NotFoundException, SQLException, ObjectReferenceException {

    Specialty type = getSpecialty(specialtyId);
    if (type == null) {
      throw new NotFoundException("type not found");
    }

    String query = "DELETE FROM specialties WHERE id = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(query)) {
      stmt.setLong(1, specialtyId);
      stmt.executeUpdate();

      return type;

    } catch (SQLException e) {
      e.printStackTrace();
      if (e.getSQLState().equals("23503")) {
        throw new ObjectReferenceException();
      }
      throw new SQLException();
    }
  }

  // NOTE: Visit Methods
  // -----------------------------------------------------------------------------------------------

  /**
   * get the visit from id
   * 
   * @param id
   * @return the visit or null if not exists
   * @throws SQLException
   */
  public Visit getVisit(long id) throws SQLException {
    String query = "SELECT * FROM visits WHERE id=?";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement preparedStatement = connection.prepareStatement(query)) {

      preparedStatement.setLong(1, id);
      ResultSet rs = preparedStatement.executeQuery();
      if (!rs.next()) {
        return null;
      }

      Visit visit = new Visit();
      visit.setId(id);
      visit.setDate(LocalDate.parse(rs.getDate("visit_date").toString()));
      visit.setDescription(rs.getString("description"));
      visit.setPetId(rs.getLong("pet_id"));
      return visit;
    } catch (SQLException e) {
      e.printStackTrace();
      throw new SQLException();
    }

  }

  /**
   * @return a Set of visits
   * @throws SQLException
   */
  public Set<Visit> listVisits() throws SQLException {
    String query = "SELECT id FROM visits";
    Set<Visit> visits = new HashSet<>();
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query)) {
      while (rs.next()) {
        Visit visit = getVisit(rs.getLong("id"));
        visits.add(visit);

      }
      return visits;
    } catch (SQLException e) {
      e.printStackTrace();
      throw new SQLException();
    }
  }

  /**
   * adds a visit
   * 
   * @param visit
   * @throws SQLException
   * @throws NamingException
   */
  public void addVisit(Visit visit) throws SQLException, NamingException {
    System.out.println("hello?");

    String query = "INSERT INTO visits (visit_date,description) VALUES (?,?)";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {

      stmt.setDate(1, Date.valueOf(visit.getDate()));
      stmt.setString(2, visit.getDescription());

      stmt.executeUpdate();
      try (ResultSet keys = stmt.getGeneratedKeys()) {
        if (!keys.next()) {
          throw new SQLException("No key");
        }
        Long key = keys.getLong(1);
        visit.setId(key.intValue());

      }
    } catch (SQLException e) {
      e.printStackTrace();
      throw new SQLException();
    }
  }

  /**
   * adds a visit to a pet
   * 
   * @param visit
   * @throws SQLException
   * @throws NamingException
   */
  public void addVisitToPet(Visit visit, long ownerId, long petId)
      throws SQLException, NamingException, NotFoundException {

    // get pet if exits and has owner

    Pet pet = getPet(petId);
    if (pet == null) {
      throw new NotFoundException();
    }
    long ownerId_stored = getOwner(ownerId).getId();
    if (ownerId_stored == 0) {
      throw new NotFoundException();
    }

    if (pet.getOwnerId() != ownerId_stored) {
      throw new NotFoundException();
    }

    String query = "INSERT INTO visits (visit_date,description,pet_id) VALUES (?,?,?)";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {

      stmt.setDate(1, Date.valueOf(visit.getDate()));
      stmt.setString(2, visit.getDescription());
      stmt.setLong(3, pet.getId());

      visit.setPetId(pet.getId());

      stmt.executeUpdate();
      try (ResultSet keys = stmt.getGeneratedKeys()) {
        if (!keys.next()) {
          throw new SQLException("No key");
        }
        Long key = keys.getLong(1);
        visit.setId(key.intValue());

      }
    } catch (SQLException e) {
      e.printStackTrace();
      throw new SQLException();
    }
  }

  /**
   * update the given visit
   * 
   * @param visit
   * @return
   * @throws NotFoundException
   * @throws SQLException
   * @throws NamingException
   */
  public Visit updateVisit(Visit visit) throws NotFoundException, SQLException, NamingException {
    Visit storedVisit = getVisit(visit.getId());
    if (storedVisit == null) {
      throw new NotFoundException();
    }
    if (visit.getDescription() == null || visit.getDescription() == "" ||
        visit.getDate() == null || visit.getDate().toString() == "") {
      throw new NamingException();
    }
    String query = " UPDATE visits SET description = ?, visit_date = ? WHERE id = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(query);) {
      stmt.setString(1, visit.getDescription());
      stmt.setDate(2, Date.valueOf(visit.getDate()));
      stmt.setLong(3, storedVisit.getId());

      stmt.executeUpdate();

      visit.setPetId(storedVisit.getPetId());

      visit.setId(storedVisit.getId());

      return visit;
    } catch (SQLException e) {
      e.printStackTrace();
      throw new SQLException();
    }

  }

  /**
   * delete visit
   * 
   * @param visitId
   * @return
   * @throws NotFoundException
   * @throws SQLException
   * @throws ObjectReferenceException
   */
  public Visit deleteVisit(long visitId) throws NotFoundException, SQLException, ObjectReferenceException {

    Visit visit = getVisit(visitId);
    if (visit == null) {
      throw new NotFoundException("visit not found");
    }

    String query = "DELETE FROM visits WHERE id = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(query)) {
      stmt.setLong(1, visitId);
      stmt.executeUpdate();

      return visit;

    } catch (SQLException e) {
      e.printStackTrace();
      if (e.getSQLState().equals("23503")) {
        throw new ObjectReferenceException();
      }
      throw new SQLException();
    }
  }

  // NOTE: Vet
  /**
   * @param vetId
   * @return vet with that id if exits or null if it doesn't
   * @throws SQLException
   */
  public Vet getVet(long vetId) throws SQLException {
    String query_vet = "SELECT * FROM vets WHERE id = ?";
    String query_spec = "SELECT specialty_id FROM vet_specialties WHERE vet_id = ? ";
    Set<Specialty> specialties = new HashSet<>();
    Vet vet = new Vet();
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(query_vet);
        PreparedStatement stmt_spec = conn.prepareStatement(query_spec)) {

      stmt.setLong(1, vetId);
      ResultSet rs = stmt.executeQuery();
      if (!rs.next()) {
        return null;
      }
      vet.setId(rs.getInt("id"));
      vet.setFirstName(rs.getString("first_name"));
      vet.setLastName(rs.getString("last_name"));

      // we set his pets
      stmt_spec.setLong(1, vetId);
      ResultSet rs_spec = stmt_spec.executeQuery();
      while (rs_spec.next()) {
        specialties.add(getSpecialty(rs_spec.getLong("specialty_id")));
      }

      vet.setSpecialties(specialties);
      return vet;

    } catch (SQLException e) {
      e.printStackTrace();
      throw new SQLException();
    }
  }

  /**
   * @return a set of vets
   * @throws SQLException
   */
  public Set<Vet> listVets() throws SQLException {
    String query = "SELECT id FROM vets";
    Set<Vet> vets = new HashSet<>();
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query)) {
      while (rs.next()) {
        Vet vet = getVet(rs.getLong("id"));
        vets.add(vet);
      }
    } catch (SQLException e) {
      e.printStackTrace();
      throw new SQLException();
    }
    return vets;
  }

  /**
   * @param vet
   *            add a new vet if the Type exists
   * @exception BadRequestException if type do not exists or its id does not match
   */
  public Vet addVet(Vet vet) throws SQLException, NotFoundException {
    // WARNING: A Vet can have more than 1 specialty
    // NOTE: Pseudocode to better understand this complex method:
    /*
     *
     * Initialize an empty array specialties_id
     * 
     * For each specialty in vet.specialties():
     * If specialty does not exist:{
     * Return NotFoundException
     * }
     * 
     * Add specialty.id to specialties_id array
     * 
     * Create the Vet and store the generated_key
     * 
     * For each id in specialties_id:{
     * Connect the specialty with the Vet using the generated_key
     * }
     * 
     * Return the Vet
     */

    String query_vet = "INSERT INTO vets (first_name, last_name) VALUES (?,?)";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(query_vet, Statement.RETURN_GENERATED_KEYS)) {

      Set<Specialty> specialties_from_request = vet.getSpecialties();
      Set<Long> specialtieId_set = new HashSet<>();

      for (Specialty specialty : specialties_from_request) {
        Specialty stored_specialty = getSpecialtyByName(specialty.getName());
        if (stored_specialty == null) {
          throw new NotFoundException();
        }
        specialty.setId(stored_specialty.getId());
        specialtieId_set.add(stored_specialty.getId());
      }

      stmt.setString(1, vet.getFirstName());
      stmt.setString(2, vet.getLastName());
      stmt.executeUpdate();

      long vet_key;
      try (ResultSet keys = stmt.getGeneratedKeys()) {
        if (!keys.next()) {
          throw new SQLException("No key");
        }
        vet_key = keys.getLong(1);
        vet.setId(vet_key); // for json Serialization

      }

      vet_specialty_bind(vet_key, specialtieId_set);

      return vet;

    } catch (SQLException e) {
      e.printStackTrace();
      throw new SQLException();
    }

  }

  /**
   * For each id in specialties_id:{
   * Connect the specialty with the Vet using the generated_key
   * }
   * 
   * 
   * @param vet_key
   * @param specialtieId_set
   */
  private void vet_specialty_bind(long vet_key, Set<Long> specialtieId_set) throws SQLException {

    String query_bind = "INSERT INTO vet_specialties (vet_id, specialty_id) VALUES (?,?)";

    try (Connection connection = dataSource.getConnection();
        PreparedStatement preparedStatement = connection.prepareStatement(query_bind)) {
      preparedStatement.setLong(1, vet_key);
      for (Long specialty_id : specialtieId_set) {
        preparedStatement.setLong(2, specialty_id);
        preparedStatement.executeUpdate();
      }

    } catch (SQLException e) {
      throw new SQLException("vet_specialty_bind went wrong");

    }

  }

  /**
   * update vet details
   * 
   * @param vet
   * @return
   * @throws SQLException
   * @throws NotFoundException
   */
  public Vet updateVet(Vet vet) throws SQLException, NotFoundException {

    if (getVet(vet.getId()) == null) {
      throw new NotFoundException("vet not found");
    }

    // WARNING: This is not the most efficient way to do it, but it get the job
    // done.
    // delete all the specialties binded with the vet and put the new ones.
    Set<Specialty> specialties = vet.getSpecialties();
    for (Specialty specialty : specialties) {
      Specialty temp_specialty = getSpecialtyByName(specialty.getName());
      if (temp_specialty == null) {
        throw new NotFoundException();
      }
      specialty.setId(temp_specialty.getId());// for json Serialization
    }

    deleteVetSpecialties(specialties, vet.getId());

    String query_update_vet = "UPDATE vets SET first_name = ?, last_name = ? WHERE id = ?";
    String query_bind_vet_spec = "INSERT INTO vet_specialties (vet_id,specialty_id) VALUES (?,?)";

    try (Connection connection = dataSource.getConnection();
        PreparedStatement preparedStatement_update = connection.prepareStatement(query_update_vet);
        PreparedStatement preparedStatement_insert = connection.prepareStatement(query_bind_vet_spec)) {

      // update vet details
      preparedStatement_update.setString(1, vet.getFirstName());
      preparedStatement_update.setString(2, vet.getLastName());
      preparedStatement_update.setLong(3, vet.getId());
      preparedStatement_update.executeUpdate();

      // insert vet_spec bind
      for (Specialty specialty : specialties) {
        preparedStatement_insert.setLong(1, vet.getId());
        preparedStatement_insert.setLong(2, specialty.getId());
        preparedStatement_insert.executeUpdate();
      }
      return vet;

    } catch (SQLException e) {
      e.printStackTrace();
      throw new SQLException();
    }

  }

  /**
   * deletes all the instances in the database that bind the vet with his
   * specialties
   * 
   * @param specialties
   * @param vet_id
   * @throws SQLException
   */
  private void deleteVetSpecialties(Set<Specialty> specialties, long vet_id) throws SQLException {

    String query_delete = "DELETE FROM vet_specialties WHERE vet_id = ?";

    try (Connection connection = dataSource.getConnection();
        PreparedStatement preparedStatement = connection.prepareStatement(query_delete)) {
      for (Specialty specialty : specialties) {
        preparedStatement.setLong(1, vet_id);
        preparedStatement.executeUpdate();
      }

    }

  }

  public Vet deleteVet(long vetId) throws SQLException, NotFoundException {
    Vet vet = getVet(vetId);

    if (vet == null) {
      throw new NotFoundException("Vet not found");
    }

    deleteVetSpecialties(vet.Specialties, vetId);

    String query = "DELETE FROM vets WHERE id = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(query)) {

      stmt.setLong(1, vetId);
      stmt.executeUpdate();

      return vet;
    }

  }

}
