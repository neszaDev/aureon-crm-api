package com.example.boilerplate.crm.common;

import com.example.boilerplate.common.entity.AuditableEntity;
import com.example.boilerplate.crm.company.CompanyRepository;
import com.example.boilerplate.crm.contact.ContactRepository;
import com.example.boilerplate.crm.deal.DealRepository;
import com.example.boilerplate.crm.lead.LeadRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

/**
 * What a page of records (tasks, activities) shows for the companies, contacts, leads and deals
 * they link: one query per kind for the whole page, none when the page links no such record. The
 * repositories are tenant-filtered, so an id of another workspace isn't found.
 */
@Component
public class LinkRefs {
  private final CompanyRepository companies;
  private final ContactRepository contacts;
  private final LeadRepository leads;
  private final DealRepository deals;

  public LinkRefs(
      CompanyRepository companies,
      ContactRepository contacts,
      LeadRepository leads,
      DealRepository deals) {
    this.companies = companies;
    this.contacts = contacts;
    this.leads = leads;
    this.deals = deals;
  }

  /** The ids as the page's records have them: null (no link) and repeated ids are fine. */
  public Refs of(
      Collection<Long> companyIds,
      Collection<Long> contactIds,
      Collection<Long> leadIds,
      Collection<Long> dealIds) {
    return new Refs(
        load(companies, companyIds, c -> new Ref(c.getId(), c.getName())),
        load(contacts, contactIds, c -> new PersonRef(c.getId(), c.getFullName())),
        load(leads, leadIds, l -> new PersonRef(l.getId(), l.getFullName())),
        load(deals, dealIds, d -> new Ref(d.getId(), d.getName())));
  }

  /** The loaded refs by id; each lookup takes the record's own, possibly null, link. */
  public record Refs(
      Map<Long, Ref> companies,
      Map<Long, PersonRef> contacts,
      Map<Long, PersonRef> leads,
      Map<Long, Ref> deals) {
    public Ref company(Long id) {
      return id == null ? null : companies.get(id);
    }

    public PersonRef contact(Long id) {
      return id == null ? null : contacts.get(id);
    }

    public PersonRef lead(Long id) {
      return id == null ? null : leads.get(id);
    }

    public Ref deal(Long id) {
      return id == null ? null : deals.get(id);
    }
  }

  private static <E extends AuditableEntity, V> Map<Long, V> load(
      JpaRepository<E, Long> rows, Collection<Long> ids, Function<E, V> ref) {
    List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
    if (distinct.isEmpty()) return Map.of();
    return rows.findAllById(distinct).stream()
        .collect(Collectors.toMap(AuditableEntity::getId, ref));
  }
}
